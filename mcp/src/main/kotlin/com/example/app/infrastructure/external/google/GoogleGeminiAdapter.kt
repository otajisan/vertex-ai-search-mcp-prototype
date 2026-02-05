package com.example.app.infrastructure.external.google

import com.example.app.domain.model.rag.DocumentResult
import com.example.app.domain.model.rag.SearchQuery
import com.example.app.domain.repository.LlmGenerationRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import org.springframework.util.StreamUtils
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Vertex AI Gemini を用いた LlmGenerationRepository 実装。
 * Python の ContentGenerator（generate_search_params, generate_answer_from_context）を移植。
 * プロンプトは検証済みの文言をそのまま使用する。
 */
@Component
class GoogleGeminiAdapter(
    private val vertexAiHolder: VertexAiHolder,
    private val resourceLoader: ResourceLoader,
    @Autowired(required = false) private val objectMapper: ObjectMapper?,
    @Value("\${vertex.gemini.model:gemini-2.0-flash}") private val modelName: String,
) : LlmGenerationRepository {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper: ObjectMapper get() = objectMapper ?: ObjectMapper()

    override suspend fun generateSearchParams(userQuery: String): SearchQuery = withContext(Dispatchers.IO) {
        val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
        val template = loadPrompt("prompts/search_params.txt")
            .replace("{{current_date}}", currentDate)
            .replace("{{user_query}}", userQuery)

        val response = vertexAiHolder.generateContent(modelName, template)
        val text = response?.trim() ?: return@withContext SearchQuery(query = userQuery)

        var jsonText = text
        if (jsonText.startsWith("```json")) jsonText = jsonText.removePrefix("```json")
        if (jsonText.startsWith("```")) jsonText = jsonText.removePrefix("```")
        if (jsonText.endsWith("```")) jsonText = jsonText.dropLast(3)
        jsonText = jsonText.trim()

        try {
            val obj = mapper.readTree(jsonText)
            SearchQuery(
                query = obj.path("query").asText().ifBlank { userQuery },
                filterStr = obj.path("filter").takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() },
                orderBy = obj.path("order_by").takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() },
            )
        } catch (e: Exception) {
            log.warn("検索パラメータのJSONパースに失敗しました。元のクエリを使用します: {}", e.message)
            SearchQuery(query = userQuery)
        }
    }

    override suspend fun generateAnswerFromContext(
        query: String,
        searchResults: List<DocumentResult>,
    ): String = withContext(Dispatchers.IO) {
        val contextParts = searchResults.mapIndexed { i, doc ->
            listOf(
                "[Document ${i + 1}] ${doc.title}",
                "URL: ${doc.url}",
                "内容:\n${doc.content}",
            ).joinToString("\n")
        }
        val context = contextParts.joinToString("\n\n")

        val template = loadPrompt("prompts/answer_from_context.txt")
            .replace("{{context}}", context)
            .replace("{{query}}", query)

        vertexAiHolder.generateContent(modelName, template) ?: "回答を生成できませんでした。"
    }

    private fun loadPrompt(path: String): String {
        val resource = resourceLoader.getResource("classpath:$path")
        return StreamUtils.copyToString(resource.inputStream, StandardCharsets.UTF_8)
    }
}
