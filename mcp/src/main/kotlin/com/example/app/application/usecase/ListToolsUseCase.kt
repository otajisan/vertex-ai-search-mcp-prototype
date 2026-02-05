package com.example.app.application.usecase

import com.example.app.domain.model.rag.RagTool
import org.springframework.stereotype.Service

/**
 * 利用可能な MCP ツール一覧を返すユースケース。
 */
@Service
class ListToolsUseCase(
    private val ragTools: List<RagTool>,
) {
    fun list(): List<RagTool> = ragTools
}
