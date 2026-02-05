package com.example.app

import com.example.app.infrastructure.external.google.VertexAiHolder
import com.google.cloud.discoveryengine.v1.SearchServiceClient
import org.mockito.Mockito.mock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
@Profile("test")
class TestConfiguration {

	@Bean
	@Primary
	fun searchServiceClient(): SearchServiceClient = mock(SearchServiceClient::class.java)

	@Bean
	@Primary
	fun vertexAiHolder(): VertexAiHolder = mock(VertexAiHolder::class.java)
}
