package com.example.app.domain.model.rag

/**
 * RAG ツールの定義。
 * データストアID・説明・システムプロンプト等を保持し、
 * list_tools および実行時に参照される。
 */
data class RagTool(
    val name: String,
    val description: String,
    val dataStoreId: String,
    val systemPrompt: String? = null,
)
