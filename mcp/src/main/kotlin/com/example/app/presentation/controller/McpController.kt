package com.example.app.presentation.controller

import com.example.app.application.usecase.ExecuteToolUseCase
import com.example.app.application.usecase.ListToolsUseCase
import com.example.app.presentation.dto.JsonRpcError
import com.example.app.presentation.dto.JsonRpcRequest
import com.example.app.presentation.dto.JsonRpcResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class McpController(
    private val listToolsUseCase: ListToolsUseCase,
    private val executeToolUseCase: ExecuteToolUseCase,
) {

    @PostMapping(
        "/mcp",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun handleJsonRpc(@RequestBody request: JsonRpcRequest): JsonRpcResponse {
        val id = request.id
        return try {
            when (request.method) {
                "initialize" -> JsonRpcResponse(
                    result = mapOf(
                        "protocolVersion" to "2024-11-05",
                        "capabilities" to mapOf("tools" to mapOf<String, Any>()),
                        "serverInfo" to mapOf(
                            "name" to "vertex-ai-search-mcp",
                            "version" to "0.0.1",
                        ),
                    ),
                    id = id,
                )
                "tools/list" -> {
                    val tools = listToolsUseCase.list().map { tool ->
                        mapOf(
                            "name" to tool.name,
                            "description" to tool.description,
                            "inputSchema" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "query" to mapOf(
                                        "type" to "string",
                                        "description" to "検索キーワードや質問内容",
                                    ),
                                ),
                                "required" to listOf("query"),
                            ),
                        )
                    }
                    JsonRpcResponse(result = mapOf("tools" to tools), id = id)
                }
                "tools/call" -> {
                    val params = request.params ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val args = (params["arguments"] as? Map<String, Any?>) ?: emptyMap()
                    val name = params["name"] as? String ?: ""
                    val result = executeToolUseCase.execute(name, args)
                    val content = listOf(
                        mapOf(
                            "type" to "text",
                            "text" to result.text,
                        ),
                    )
                    JsonRpcResponse(
                        result = mapOf(
                            "content" to content,
                            "isError" to result.isError,
                        ),
                        id = id,
                    )
                }
                else -> JsonRpcResponse(
                    error = JsonRpcError(code = -32601, message = "Method not found: ${request.method}"),
                    id = id,
                )
            }
        } catch (e: Exception) {
            JsonRpcResponse(
                error = JsonRpcError(code = -32603, message = "Internal error: ${e.message}"),
                id = id,
            )
        }
    }

    @GetMapping(value = ["/sse"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sse(): Flow<ServerSentEvent<String>> = flow {
        emit(
            ServerSentEvent.builder<String>()
                .event("endpoint")
                .data("{\"url\":\"/mcp\"}")
                .build(),
        )
    }
}
