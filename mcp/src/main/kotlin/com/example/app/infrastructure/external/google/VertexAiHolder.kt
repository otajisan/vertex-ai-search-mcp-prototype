package com.example.app.infrastructure.external.google

import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.generativeai.GenerativeModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import javax.annotation.PreDestroy

/**
 * Vertex AI クライアントのホルダー。
 * Gemini によるテキスト生成で利用する。
 * Vertex AI (PredictionService) は regional エンドポイントのみ対応のため、
 * location が "global" の場合は us-central1 にフォールバックする。
 */
@Component
@Profile("!test")
class VertexAiHolder(
    @Value("\${vertex.gemini.project-id:\${vertex.search.project-id}}") private val projectId: String,
    @Value("\${vertex.gemini.location:us-central1}") private val location: String,
) {
    private val vertexAi: VertexAI by lazy {
        val region = if (location == "global") "us-central1" else location
        VertexAI(projectId, region)
    }

    fun generateContent(modelName: String, prompt: String): String? {
        val model = GenerativeModel(modelName, vertexAi)
        val response = model.generateContent(prompt)
        val candidate = response.candidatesList?.firstOrNull() ?: return null
        return candidate.content?.partsList?.firstOrNull()?.text
    }

    @PreDestroy
    fun close() {
        vertexAi.close()
    }
}
