# mcp-bridge

Claude Desktop（stdio JSON-RPC）と MCP サーバー（HTTPS/SSE）を繋ぐ CLI ブリッジです。

## 必要な環境

- Go 1.22+（推奨: `mise` で `mise install` 後に `mise exec -- go` を使用）

## 依存関係の取得

```bash
cd client
go get github.com/spf13/cobra github.com/spf13/viper
```

または、ビルド時に自動解決されます。

```bash
go build -o mcp-bridge ./cmd/mcp-bridge
```

## 使い方

### プロキシの開始（接続待機）

```bash
go run ./cmd/mcp-bridge connect
```

デフォルトで `http://localhost:8080/sse` に接続します。別の URL を指定する場合:

```bash
go run ./cmd/mcp-bridge connect --url http://localhost:8080/sse --debug
```

- `--url`: MCP サーバーの SSE エンドポイント URL（デフォルト: `http://localhost:8080/sse`）
- `--debug`: デバッグログを stderr に出力

実行すると待機状態になり、標準入力から JSON-RPC を読み取り、サーバーへ POST してレスポンスを標準出力に書き出します。

### 疎通確認

1. 別ターミナルで Kotlin MCP サーバーを起動する（例: `cd mcp && ./gradlew bootRun`）。
2. このディレクトリで `go run ./cmd/mcp-bridge connect` を実行する。
3. 標準入力に 1 行で JSON-RPC を送る（例: `echo '{"jsonrpc":"2.0","method":"initialize","params":{},"id":1}' | go run ./cmd/mcp-bridge connect`）。
4. 標準出力に JSON-RPC レスポンスが返れば疎通成功です。

### install（プレースホルダー）

Claude Desktop の設定ファイルを書き換える枠組みです。未実装です。

```bash
go run ./cmd/mcp-bridge install
```

## 設定

- 環境変数: `MCP_BRIDGE_URL`, `MCP_BRIDGE_PROFILE`, `MCP_BRIDGE_DEBUG`
- 設定ファイル（任意）: カレントまたは `$HOME` に `.mcp-bridge.yaml`
