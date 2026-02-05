package com.example.app.domain.repository

import com.example.app.domain.model.rag.DocumentResult
import com.example.app.domain.model.rag.SearchQuery

/**
 * LLM（Gemini）による生成リポジトリのインターフェース。
 * 検索パラメータ抽出と回答生成を Infrastructure 層に委譲する。
 */
interface LlmGenerationRepository {

    /**
     * 自然言語の質問から検索パラメータ（query, filter, order_by）を抽出する。
     */
    suspend fun generateSearchParams(userQuery: String): SearchQuery

    /**
     * 検索結果をコンテキストとして、ユーザーの質問に対する回答を生成する。
     */
    suspend fun generateAnswerFromContext(
        query: String,
        searchResults: List<DocumentResult>,
    ): String
}
