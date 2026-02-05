# プロジェクトアーキテクチャ詳細設計書

## 1. プロジェクト概要
本リポジトリは、企業内ドキュメント検索（RAG）を **MCP (Model Context Protocol)** サーバーとして提供するためのモノレポプロジェクトです。
ユーザー（Claude Desktop等）と社内ナレッジベース（Google Vertex AI Search）をセキュアに接続し、LLM（Gemini）による高度な回答生成を実現します。

### 主要な特徴
- **ハイブリッドクラウド構成**: アプリケーションと認証は AWS (Fargate/Cognito) で、AIとデータ検索は GCP (Vertex AI) で実行します。
- **クライアント配布の簡略化**: ユーザーには Go言語製のシングルバイナリツールを配布し、複雑なPython環境構築を不要にします。
- **マルチRAG対応**: サーバーサイドは複数のRAGツール（例: 議事録検索、人事規定検索）をホスト可能なプラットフォームとして設計されています。

## 2. モノレポ構成と技術スタック

本リポジトリは以下の3つの主要コンポーネントで構成されています。

### `mcp/` (Server Application)
MCPサーバーのコアロジックを担当するバックエンドアプリケーションです。
- **言語**: Kotlin (JDK 25)
- **フレームワーク**: Spring Boot 3.2+ (Spring WebFlux)
- **アーキテクチャ**: オニオンアーキテクチャ (DDD)
- **通信プロトコル**: Server-Sent Events (SSE) for MCP transport
- **認証**: Spring Security + AWS Cognito (OAuth2 Resource Server)

### `client/` (Client CLI Tool)
ユーザーのローカル環境で動作するブリッジアプリケーションです。
- **言語**: Go (Golang) 1.22+
- **配布形式**: シングルバイナリ (Windows .exe / macOS binary)
- **主要機能**:
    - `install`: Claude Desktop 設定ファイルの自動更新
    - `login`: AWS Cognito との認証フロー（ブラウザ起動）とトークン管理
    - `connect`: Stdio (JSON-RPC) と HTTPS (SSE) の相互変換プロキシ

### `scripts/` (Ops & Data Pipeline)
データの準備と運用を行うためのスクリプト群です。
- **言語**: Python 3.12+
- **ツール**: `uv` (パッケージ管理), Vertex AI SDK
- **主要機能**:
    - 非構造化データ（PDF/Office）からのメタデータ抽出
    - GCSへのアップロードと Vertex AI Search へのインデックス登録
    - 検索精度（Extractive Segments）の検証

## 3. システムデータフロー

ユーザーが「1月の朝会のサマリを教えて」と質問した際の処理フローは以下の通りです。

1.  **User Input**: ユーザーが Claude Desktop に質問を入力。
2.  **MCP Routing**: Claude は `mcp-bridge` (Go Client) をサブプロセスとして起動し、JSON-RPC でツール実行を要求。
3.  **Auth Proxy**: Client はローカルに保存された Cognito トークンをヘッダーに付与し、AWS 上の Server へ HTTPS (SSE) でリクエスト転送。
4.  **Security Check**: Server (Spring Security) が JWT トークンを検証し、ユーザーの所属グループ（権限）を確認。
5.  **Tool Selection**: `ExecuteToolUseCase` がリクエストされたツール名（例: `search_minutes`）に基づき、適切な `RagTool` 設定（データストアID等）を選択。
6.  **Retrieval (GCP)**: Vertex AI Search API を呼び出し。メタデータフィルタ（`date >= ...`）とセマンティック検索を実行。
7.  **Generation (GCP)**: 検索結果をコンテキストとして Vertex AI Gemini に渡し、回答を生成。
8.  **Response**: 生成された回答が Server -> Client -> Claude へと返却され、ユーザーに表示される。

## 4. サーバーサイド設計詳細 (`mcp/`)

サーバーサイドは厳格な **オニオンアーキテクチャ** を採用しており、依存関係は常に「内側」に向かいます。

### Domain Layer (`domain/`)
- **責務**: ビジネスロジックとルールの定義。フレームワークやクラウドSDKには一切依存しません。
- **主要モデル**:
    - `RagTool`: RAGツールの定義（ID、説明、データストア設定、プロンプト）。
    - `SearchQuery`: ユーザーの意図から抽出されたクエリとフィルタ条件。
    - `SearchResult`: ドキュメントの断片（Segments）と出典情報。

### Application Layer (`application/`)
- **責務**: ドメインオブジェクトの調整（オーケストレーション）。
- **主要コンポーネント**:
    - `ExecuteToolUseCase`: 指定されたツール設定を用いて検索・生成サービスを呼び出す。
    - `ListToolsUseCase`: 利用可能なツール定義の一覧を返す。

### Infrastructure Layer (`infrastructure/`)
- **責務**: インターフェースの実装、外部API通信、DIコンテナの設定。
- **設計のポイント**:
    - **Configuration as Code**: 具体的なRAGツール（議事録用、技術資料用など）は、クラスとして実装するのではなく、`@Configuration` クラス内で `RagTool` の **Bean** として定義します。これにより、ロジックを変更せずに設定だけで新しい検索ツールを追加できます。

### Presentation Layer (`presentation/`)
- **責務**: HTTP/SSE リクエストのハンドリング。
- **実装**: Spring WebFlux コントローラーを用いて、非同期ストリームとして MCP メッセージを処理します。

## 5. クライアントサイド設計詳細 (`client/`)

Go言語によるクライアントは、依存関係を持たない「土管（dumb pipe）」として設計されています。

- **Cobra & Viper**: CLIコマンド構造と設定管理に使用。
- **Concurrency**: `Stdin` の読み込みループと `SSE` の受信ループを個別の Goroutine で実行し、Channel を介してデータをやり取りすることで、ノンブロッキングな双方向通信を実現しています。
- **Installer Logic**: OS (Windows/Mac) を判別し、Claude Desktop の設定ファイルパス（`User/AppData/...` や `Library/Application Support/...`）を自動特定して書き換えます。
