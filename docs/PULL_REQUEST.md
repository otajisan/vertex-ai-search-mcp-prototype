# Pythonプロトタイプからのロジック移植とMCPサーバー実装

Closes #1

## 概要

`notebooklm-enterprise-experiments-py` で検証済みの検索・フィルタ・プロンプトロジックを、本リポジトリの `mcp/`（Kotlin x Spring Boot）に移植し、MCP サーバーとして動作するようにしました。ロジックの再実装は行わず、**Kotlin/DDD アーキテクチャへの適合（Translation）** に集中しています。

## 変更内容

### 1. 依存関係（`mcp/build.gradle.kts`）

- Spring Boot WebFlux（SSE・非同期）
- Kotlin Coroutines（`kotlinx-coroutines-core`, `kotlinx-coroutines-reactor`）
- Google Cloud Vertex AI（`google-cloud-vertexai:1.42.0`）
- Google Cloud Discovery Engine（`google-cloud-discoveryengine:0.79.0`）
- Jackson `jackson-databind`
- Java ツールチェーンを 24 → 21 に変更

### 2. Domain 層（`mcp/src/main/kotlin/.../domain/`）

| ファイル | 説明 |
|----------|------|
| `model/rag/SearchQuery.kt` | 検索クエリとフィルタ（query, filterStr, orderBy） |
| `model/rag/SearchCitation.kt` | 引用情報（title, url） |
| `model/rag/DocumentResult.kt` | 検索ドキュメント（title, content, url） |
| `model/rag/SearchResult.kt` | DocumentSearchResult / SearchResult |
| `model/rag/RagTool.kt` | ツール定義（name, description, dataStoreId, systemPrompt） |
| `repository/DocumentSearchRepository.kt` | ドキュメント検索リポジトリ IF |
| `repository/LlmGenerationRepository.kt` | LLM 生成リポジトリ IF（検索パラメータ抽出・回答生成） |

Python の `models/search.py`（Pydantic）を Kotlin の `data class` に変換。

### 3. Infrastructure 層（`mcp/src/main/kotlin/.../infrastructure/`）

| ファイル | 説明 |
|----------|------|
| `external/google/GoogleSearchAdapter.kt` | Discovery Engine による検索。Python の `search_documents` / `_parse_document_response` を移植。**フィルタは `date` を使用**（structData.date ではない）。フィルタ/ソート構文エラー時はフィルタなしで再検索。 |
| `external/google/GoogleGeminiAdapter.kt` | `ContentGenerator` の `generate_search_params`・`generate_answer_from_context` を移植。プロンプトは検証済み文言をそのまま使用。 |
| `external/google/VertexAiHolder.kt` | Vertex AI Gemini クライアントのホルダー。 |
| `configuration/DiscoveryEngineConfig.kt` | `SearchServiceClient` Bean（location に応じたエンドポイント）。 |
| `configuration/RagToolsConfig.kt` | `search_documents` 用 `RagTool` Bean。 |

プロンプトは `resources/prompts/search_params.txt` および `answer_from_context.txt` に配置（Python の文字列をそのまま移植）。

### 4. Application 層（`mcp/src/main/kotlin/.../application/`）

| ファイル | 説明 |
|----------|------|
| `usecase/ExecuteToolUseCase.kt` | 1. Gemini でクエリ生成 → 2. Search 実行 → 3. Gemini で回答生成（Python の `rag_server` + `content_generator` 連携に相当）。 |
| `usecase/ListToolsUseCase.kt` | 利用可能ツール一覧を返す。 |

### 5. Presentation 層（`mcp/src/main/kotlin/.../presentation/`）

| ファイル | 説明 |
|----------|------|
| `controller/McpController.kt` | **POST /mcp**: JSON-RPC 2.0（`initialize`, `tools/list`, `tools/call`）。**GET /sse**: `Flow<ServerSentEvent<String>>` の SSE エンドポイント。 |
| `dto/McpJsonRpc.kt` | JSON-RPC リクエスト/レスポンス DTO。 |

### 6. 設定・テスト

- **`application.yml`**: `vertex.search.*`（project-id, location, engine-id）、`vertex.gemini.*`（project-id, location, model）を追加。未設定時は `default-project` / `default-engine` で起動可能。
- **テスト**: GCP 認証が必要な Bean は `@Profile("!test")` で本番のみ有効化。テスト時は `TestConfiguration` で `SearchServiceClient` と `VertexAiHolder` をモック化。`./gradlew test` が成功。

## アーキテクチャ準拠

- `.cursor/rules/mcp-server.mdc` に従い、**Inside-Out**（Domain → Application → Infrastructure → Presentation）で実装。
- 非同期は **Kotlin Coroutines**（`suspend fun`）で統一。
- **derivedStructData** は Infrastructure 層で型付きドメインモデル（`DocumentResult`）に変換。
- フィルタは検索キーワードと分離し、`filter` パラメータで扱う。

## 動作確認

- `./gradlew test` でテスト成功。
- 起動: `./gradlew bootRun`（要 `GCP_PROJECT_ID`, `ENGINE_ID` 等）。
- 検索の手動確認例:
  ```bash
  curl -X POST http://localhost:8080/mcp \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"search_documents","arguments":{"query":"議事録"}},"id":1}'
  ```

## 注意事項

- Vertex AI Java SDK（`GenerativeModel`）の deprecation 警告は現状のまま（Issue 記載のライブラリを使用）。
- 新規 RAG ツール追加は `RagToolsConfig.kt` に `@Bean` を追加するだけで対応可能。
