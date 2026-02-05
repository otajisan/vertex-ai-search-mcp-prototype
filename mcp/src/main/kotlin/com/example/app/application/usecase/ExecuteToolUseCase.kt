package com.example.app.application.usecase

import com.example.app.domain.model.rag.RagTool
import com.example.app.domain.repository.DocumentSearchRepository
import com.example.app.domain.repository.LlmGenerationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * ツール実行ユースケース。
 * 1. Gemini でクエリ生成 -> 2. Search 実行 -> 3. Gemini で回答生成（Python の rag_server + content_generator 連携に相当）
 */
@Service
class ExecuteToolUseCase(
    private val ragTools: List<RagTool>,
    private val documentSearchRepository: DocumentSearchRepository,
    private val llmGenerationRepository: LlmGenerationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(toolName: String, arguments: Map<String, Any?>): ToolResult {
        val tool = ragTools.find { it.name == toolName }
            ?: return ToolResult.error("Unknown tool: $toolName")

        if (toolName != "search_documents") {
            return ToolResult.error("Unsupported tool: $toolName")
        }

        val query = (arguments["query"] as? String)?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("エラー: queryパラメータが必要です。")

        return runCatching {
            // Step 0: クエリから検索パラメータを抽出
            val searchParams = llmGenerationRepository.generateSearchParams(query)
            val engineId = tool.dataStoreId
            if (engineId.isBlank()) {
                return ToolResult.error("エラー: 検索エンジンIDが設定されていません（vertex.search.engine-id）。")
            }

            // Step 1: 検索実行
            val searchResult = documentSearchRepository.searchDocuments(
                engineId = engineId,
                searchQuery = searchParams,
                pageSize = 20,
            )

            if (searchResult.results.isEmpty()) {
                return ToolResult.success("検索結果が見つかりませんでした。")
            }

            // Step 2: Gemini で回答生成
            val answer = llmGenerationRepository.generateAnswerFromContext(query, searchResult.results)

            // 結果フォーマット（Python 版と同様）
            val outputParts = mutableListOf(
                "## 回答",
                "",
                answer,
                "",
                "---",
                "",
            )
            if (!searchParams.filterStr.isNullOrBlank() || !searchParams.orderBy.isNullOrBlank()) {
                outputParts.add("### 検索条件")
                searchParams.filterStr?.let { outputParts.add("- フィルタ: $it") }
                searchParams.orderBy?.let { outputParts.add("- ソート: $it") }
                outputParts.add("")
            }
            outputParts.add("### 参照ドキュメント")
            searchResult.results.take(10).forEachIndexed { i, doc ->
                outputParts.add("${i + 1}. **${doc.title}**")
                if (doc.url.isNotBlank()) outputParts.add("   - URL: ${doc.url}")
            }
            outputParts.add("")

            ToolResult.success(outputParts.joinToString("\n"))
        }.getOrElse { e ->
            log.warn("Tool execution failed: {}", e.message)
            ToolResult.error("検索エラー: ${e.message}")
        }
    }
}

data class ToolResult(
    val text: String,
    val isError: Boolean,
) {
    companion object {
        fun success(text: String) = ToolResult(text = text, isError = false)
        fun error(text: String) = ToolResult(text = text, isError = true)
    }
}
