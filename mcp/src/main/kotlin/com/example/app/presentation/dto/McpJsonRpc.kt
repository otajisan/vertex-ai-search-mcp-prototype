package com.example.app.presentation.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * JSON-RPC 2.0 リクエスト／レスポンス DTO。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: Map<String, Any?>? = null,
    val id: Any? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: Any? = null,
    val error: JsonRpcError? = null,
    val id: Any? = null,
)

data class JsonRpcError(
    val code: Int,
    val message: String,
)
