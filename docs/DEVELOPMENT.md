# 開発ワークフローガイド

本プロジェクトは、KotlinサーバーとGoクライアントが連携して動作します。
効率的な開発のため、ローカル環境ですべてのスタックを動作させる「Local Development」フローを推奨しています。

## 1. 事前準備 (Prerequisites)

以下のツールがインストールされていることを確認してください。

- **Java**: JDK 21+ (`java -version`)
- **Go**: 1.22+ (`go version`)
- **Python**: 3.12+ & `uv` (`uv --version`)
- **Cloud CLI**:
    - `gcloud` (Vertex AI アクセス用)
    - `aws` (Cognito アクセス用)

## 2. クラウド認証設定

ローカルからクラウド上のリソース（Vertex AI / Cognito）にアクセスするための認証を通します。

```bash
# GCP (Vertex AI) へのアクセス権取得 (Application Default Credentials)
gcloud auth application-default login

# AWS (Cognito) へのアクセス権設定 (SSO または Access Key)
aws configure --profile my-dev-profile
```

## 3. ローカル開発の流れ

### A. サーバーサイド (Kotlin) の起動
サーバーはデフォルトで `8080` ポートで起動します。
開発中は、Cognitoによる厳格なJWT検証をバイパスする `dev` プロファイルの使用を推奨します。

```bash
cd mcp
# devプロファイルで起動 (認証を緩和、詳細ログを出力)
./gradlew bootRun --args='--spring.profiles.active=dev'
```

**開発時の設定 (`mcp/src/main/resources/application-dev.yml`):**
- `logging.level.root`: DEBUG
- セキュリティ設定: ダミーのBearerトークンを受け入れる設定にしておくと、Goクライアントなしで `curl` テストが可能です。

### B. クライアントサイド (Go) の起動
サーバーが `localhost:8080` で動いている状態で、クライアントを起動します。

```bash
cd client

# 1. 依存関係のダウンロード
go mod tidy

# 2. 接続テスト (サーバーのSSEエンドポイントへ接続)
# --insecure フラグ等を実装し、ローカルHTTP接続を許可すること
go run ./cmd/mcp-bridge connect --url http://localhost:8080/sse --debug
```

### C. Claude Desktop との統合テスト
実際に Claude Desktop からローカルのコードを呼び出す設定です。

1. `~/Library/Application Support/Claude/claude_desktop_config.json` を編集:
    ```json
    "mcpServers": {
      "local-dev-rag": {
        "command": "go",
        "args": ["run", "/path/to/repo/client/cmd/mcp-bridge", "connect", "--url", "http://localhost:8080/sse"],
        "env": {
           "AWS_PROFILE": "my-dev-profile"
        }
      }
    }
    ```
2. Claude Desktop を再起動すると、ローカルの Kotlin サーバーと通信が始まります。

## 4. データパイプラインの実行 (Python)

新しいドキュメントを追加して検索インデックスを更新する場合:

```bash
cd scripts
uv sync

# メタデータ生成とインポートのドライラン
uv run python tools/import_gcs.py --dry-run
```

## 5. テスト実行コマンド

- **Kotlin (Unit Test)**: `./gradlew test` (高速、外部通信なし)
- **Go (Unit Test)**: `go test ./internal/...`
