package com.example.app

import com.example.app.infrastructure.external.google.SearchServiceClientHolder
import com.example.app.infrastructure.external.google.VertexAiHolder
import com.google.cloud.discoveryengine.v1.SearchServiceClient
import org.mockito.Mockito.`when`
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
	fun searchServiceClientHolder(): SearchServiceClientHolder {
		val client = mock(SearchServiceClient::class.java)
		val holder = mock(SearchServiceClientHolder::class.java)
		`when`(holder.getClient()).thenReturn(client)
		return holder
	}

	@Bean
	@Primary
	fun vertexAiHolder(): VertexAiHolder = mock(VertexAiHolder::class.java)
}
