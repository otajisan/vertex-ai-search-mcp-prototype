package com.example.app.domain.repository

import com.example.app.domain.model.rag.DocumentSearchResult
import com.example.app.domain.model.rag.SearchQuery

/**
 * ドキュメント検索リポジトリのインターフェース。
 * Vertex AI Search (Discovery Engine) の実装は Infrastructure 層に委譲する。
 */
interface DocumentSearchRepository {

    /**
     * クエリとフィルタでドキュメント検索を実行する。
     * @param engineId 検索アプリ（Engine）の ID
     * @param searchQuery 検索キーワードとフィルタ・ソート条件
     * @param pageSize 取得件数
     * @return 検索結果のドキュメントリスト
     */
    suspend fun searchDocuments(
        engineId: String,
        searchQuery: SearchQuery,
        pageSize: Int = 20,
    ): DocumentSearchResult
}
