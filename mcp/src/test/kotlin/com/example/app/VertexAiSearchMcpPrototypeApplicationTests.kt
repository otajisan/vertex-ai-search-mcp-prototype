package com.example.app

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfiguration::class)
class VertexAiSearchMcpPrototypeApplicationTests {

	@Test
	fun contextLoads() {
	}
}
