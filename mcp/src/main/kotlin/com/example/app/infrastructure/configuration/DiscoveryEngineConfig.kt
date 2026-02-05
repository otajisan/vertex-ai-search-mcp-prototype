package com.example.app.infrastructure.configuration

import com.google.cloud.discoveryengine.v1.SearchServiceClient
import com.google.cloud.discoveryengine.v1.SearchServiceSettings
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.IOException
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!test")
class DiscoveryEngineConfig {

    @Bean
    @Throws(IOException::class)
    fun searchServiceClient(
        @Value("\${vertex.search.location:global}") location: String,
    ): SearchServiceClient {
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
}
