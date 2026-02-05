package com.example.app.domain.model.rag

/**
 * ドキュメント検索結果のリスト。
 */
data class DocumentSearchResult(
    val results: List<DocumentResult> = emptyList(),
)

/**
 * API 要約付き検索結果（summary + citations）。
 * search_and_answer 用。
 */
data class SearchResult(
    val summary: String,
    val citations: List<SearchCitation>,
)
