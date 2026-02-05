package com.example.app.infrastructure.external.google

import com.example.app.domain.model.rag.DocumentResult
import com.example.app.domain.model.rag.DocumentSearchResult
import com.example.app.domain.model.rag.SearchQuery
import com.example.app.domain.repository.DocumentSearchRepository
import com.google.api.gax.rpc.InvalidArgumentException
import com.google.cloud.discoveryengine.v1.SearchRequest
import com.google.cloud.discoveryengine.v1.SearchResponse
import com.google.cloud.discoveryengine.v1.SearchServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Vertex AI Search (Discovery Engine) を用いた DocumentSearchRepository 実装。
 * Python の search_documents / _parse_document_response ロジックを移植。
 * フィルタは date フィールドを使用（structData.date ではない）。
 * SearchServiceClient は @Lazy で初回利用時まで生成を遅延し、起動時に ADC が未設定でも起動可能にする。
 */
@Component
class GoogleSearchAdapter(
    @Lazy private val searchClient: SearchServiceClient,
    @Value("\${vertex.search.project-id}") private val projectId: String,
    @Value("\${vertex.search.location:global}") private val location: String,
) : DocumentSearchRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun searchDocuments(
        engineId: String,
        searchQuery: SearchQuery,
        pageSize: Int,
    ): DocumentSearchResult = withContext(Dispatchers.IO) {
        val servingConfig = "projects/$projectId/locations/$location/collections/default_collection/engines/$engineId/servingConfigs/default_serving_config"

        val snippetSpec = SearchRequest.ContentSearchSpec.SnippetSpec.newBuilder()
            .setReturnSnippet(true)
            .setMaxSnippetCount(3)
            .build()
        val extractiveSpec = SearchRequest.ContentSearchSpec.ExtractiveContentSpec.newBuilder()
            .setMaxExtractiveAnswerCount(2)
            .setMaxExtractiveSegmentCount(3)
            .setNumPreviousSegments(1)
            .setNumNextSegments(1)
            .setReturnExtractiveSegmentScore(true)
            .build()
        val contentSearchSpec = SearchRequest.ContentSearchSpec.newBuilder()
            .setSnippetSpec(snippetSpec)
            .setExtractiveContentSpec(extractiveSpec)
            .build()

        val requestBuilder = SearchRequest.newBuilder()
            .setServingConfig(servingConfig)
            .setQuery(searchQuery.query)
            .setPageSize(pageSize)
            .setContentSearchSpec(contentSearchSpec)
            .setQueryExpansionSpec(
                SearchRequest.QueryExpansionSpec.newBuilder()
                    .setCondition(SearchRequest.QueryExpansionSpec.Condition.AUTO)
                    .build(),
            )
            .setSpellCorrectionSpec(
                SearchRequest.SpellCorrectionSpec.newBuilder()
                    .setMode(SearchRequest.SpellCorrectionSpec.Mode.AUTO)
                    .build(),
            )
        if (!searchQuery.filterStr.isNullOrBlank()) {
            requestBuilder.setFilter(searchQuery.filterStr)
            log.debug("Filter: {}", searchQuery.filterStr)
        }
        if (!searchQuery.orderBy.isNullOrBlank()) {
            requestBuilder.setOrderBy(searchQuery.orderBy)
            log.debug("Order by: {}", searchQuery.orderBy)
        }

        try {
            val pagedResponse = searchClient.search(requestBuilder.build())
            val response = pagedResponse.iteratePages().firstOrNull()?.response
                ?: SearchResponse.getDefaultInstance()
            parseDocumentResponse(response)
        } catch (e: InvalidArgumentException) {
            val msg = e.message ?: ""
            val filterErrors = listOf(
                "Unsupported field",
                "Invalid filter syntax",
                "Unsupported rhs value",
                "Parsing filter failed",
            )
            if (filterErrors.any { msg.contains(it) }) {
                log.warn("フィルタ/ソート構文エラー。フィルタなしで再検索します。: {}", msg.take(200))
                val fallback = SearchRequest.newBuilder()
                    .setServingConfig(servingConfig)
                    .setQuery(searchQuery.query)
                    .setPageSize(pageSize)
                    .setContentSearchSpec(contentSearchSpec)
                    .setQueryExpansionSpec(
                        SearchRequest.QueryExpansionSpec.newBuilder()
                            .setCondition(SearchRequest.QueryExpansionSpec.Condition.AUTO)
                            .build(),
                    )
                    .setSpellCorrectionSpec(
                        SearchRequest.SpellCorrectionSpec.newBuilder()
                            .setMode(SearchRequest.SpellCorrectionSpec.Mode.AUTO)
                            .build(),
                    )
                    .build()
                val pagedResponse = searchClient.search(fallback)
                val response = pagedResponse.iteratePages().firstOrNull()?.response
                    ?: SearchResponse.getDefaultInstance()
                parseDocumentResponse(response)
            } else {
                throw e
            }
        }
    }

    private fun parseDocumentResponse(response: SearchResponse): DocumentSearchResult {
        val documents = mutableListOf<DocumentResult>()
        for (result in response.resultsList) {
            val document = result.document ?: continue
            val data = document.derivedStructData ?: continue
            val fields = data.fieldsMap

            val title = fields["title"]?.stringValue?.takeIf { it.isNotBlank() } ?: "無題"
            val url = fields["link"]?.stringValue ?: ""

            val sources = mutableListOf<com.google.protobuf.Value>()
            fields["extractive_segments"]?.listValue?.valuesList?.let { sources.addAll(it) }
            fields["extractive_answers"]?.listValue?.valuesList?.let { sources.addAll(it) }
            fields["snippets"]?.listValue?.valuesList?.let { sources.addAll(it) }

            val contentParts = sources.mapNotNull { extractTextFromSource(it) }
            var fullContent = contentParts.joinToString("\n\n")
            if (fullContent.isBlank()) fullContent = "(本文なし)"

            documents.add(
                DocumentResult(
                    title = title,
                    content = fullContent,
                    url = url,
                ),
            )
        }
        return DocumentSearchResult(results = documents)
    }

    private fun extractTextFromSource(value: com.google.protobuf.Value): String? {
        if (!value.hasStructValue()) return null
        val s = value.structValue.fieldsMap
        val content = s["content"]?.stringValue
            ?: s["snippet"]?.stringValue
            ?: s["htmlSnippet"]?.stringValue
            ?: s["text"]?.stringValue
        if (!content.isNullOrBlank()) {
            return content.replace("<b>", "").replace("</b>", "").replace("\n", " ")
        }
        var longest = ""
        for (v in s.values) {
            val str = if (v.hasStringValue()) v.stringValue else null
            if (!str.isNullOrBlank() && str.length > longest.length && str.length > 10) {
                longest = str
            }
        }
        return longest.ifBlank { null }?.replace("<b>", "")?.replace("</b>", "")?.replace("\n", " ")
    }
}
