package com.example.app.infrastructure.external.google

import com.google.cloud.discoveryengine.v1.SearchServiceClient
import com.google.cloud.discoveryengine.v1.SearchServiceSettings
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import javax.annotation.PreDestroy

/**
 * Discovery Engine SearchServiceClient のホルダー。
 * 初回利用時にクライアントを生成し、@Lazy プロキシ経由でのスタブ未初期化を防ぐ。
 */
@Component
@Profile("!test")
class SearchServiceClientHolder(
    @Value("\${vertex.search.location:global}") private val location: String,
) {
    @Volatile
    private var _client: SearchServiceClient? = null

    private val clientOrNull: SearchServiceClient
        get() {
            return _client ?: synchronized(this) {
                _client ?: createClient().also { _client = it }
            }
        }

    private fun createClient(): SearchServiceClient {
        val endpoint = if (location == "global") {
            "discoveryengine.googleapis.com:443"
        } else {
            "$location-discoveryengine.googleapis.com:443"
        }
        val settings = SearchServiceSettings.newBuilder()
            .setEndpoint(endpoint)
            .build()
        return SearchServiceClient.create(settings)
    }

    fun getClient(): SearchServiceClient = clientOrNull

    @PreDestroy
    fun close() {
        _client?.close()
    }
}
