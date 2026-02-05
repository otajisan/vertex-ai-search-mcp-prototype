package com.example.app.infrastructure.configuration

import com.example.app.domain.model.rag.RagTool
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * RAG ツールの Bean 定義。
 * 新しい検索ツールを追加する場合は、ここに新しい @Bean を追加する。
 */
@Configuration
class RagToolsConfig {

    @Bean
    fun searchDocumentsTool(
        @Value("\${vertex.search.engine-id:}") engineId: String,
    ): RagTool {
        return RagTool(
            name = "search_documents",
            description = "社内ドキュメント（PDF/Googleドライブ）を検索し、要約と引用元を返す。",
            dataStoreId = engineId,
            systemPrompt = null,
        )
    }
}
