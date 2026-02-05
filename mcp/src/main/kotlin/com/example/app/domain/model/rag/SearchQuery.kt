package com.example.app.domain.model.rag

/**
 * 検索クエリとフィルタ条件。
 * ユーザーの質問から Gemini で抽出した検索キーワードとフィルタを保持する。
 */
data class SearchQuery(
    val query: String,
    val filterStr: String? = null,
    val orderBy: String? = null,
)
