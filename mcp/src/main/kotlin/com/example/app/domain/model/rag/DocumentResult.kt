package com.example.app.domain.model.rag

/**
 * 検索ドキュメントの詳細情報。
 * スニペットまたは抽出テキストを保持する。
 */
data class DocumentResult(
    val title: String,
    val content: String,
    val url: String,
)
