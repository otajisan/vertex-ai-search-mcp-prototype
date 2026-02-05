# Goクライアントの実装 (mcp-bridge)

Closes #5

## 概要

`client/` 配下に、MCPサーバーへ接続する CLI ツール **mcp-bridge** を実装しました。ユーザーのローカル環境（Claude Desktop）と MCP サーバー（Kotlin）を stdio ↔ SSE/POST で繋ぐブリッジです。

## 変更内容

### 1. プロジェクト構成（Step 1）

| パス | 説明 |
|------|------|
| `client/cmd/mcp-bridge/main.go` | エントリポイント（cobra root） |
| `client/cmd/mcp-bridge/connect.go` | `connect` サブコマンド |
| `client/cmd/mcp-bridge/install.go` | `install` サブコマンド（プレースホルダー） |
| `client/internal/config/config.go` | 設定管理（viper） |
| `client/internal/proxy/proxy.go` | SSE クライアントと JSON-RPC プロキシ |

- 依存: `github.com/spf13/cobra`, `github.com/spf13/viper`（`go get` 済み・`go.mod`/`go.sum` に反映）

### 2. 設定管理（`internal/config`）

- **url**: 接続先 SSE エンドポイント（デフォルト: `http://localhost:8080/sse`）
- **profile**: 認証プロファイル（将来の Cognito 用、今回は未使用）
- **debug**: デバッグログ有無
- 環境変数: `MCP_BRIDGE_URL`, `MCP_BRIDGE_PROFILE`, `MCP_BRIDGE_DEBUG`
- 任意の設定ファイル: `.mcp-bridge.yaml`（カレント or `$HOME`）

### 3. SSE クライアントとプロキシ（`internal/proxy`）

- **Goroutine A**: `os.Stdin` から 1 行ずつ JSON-RPC を読み、サーバーへ **POST /mcp** し、レスポンスを stdout 用チャネルへ送信
- **Goroutine B**: **GET /sse** で SSE を受信し、JSON-RPC レスポンスと判断できるイベントの `data` のみ stdout 用チャネルへ送信（`{"url":"/mcp"}` などの endpoint 通知は転送しない）
- 単一の writer がチャネルから取り出して `os.Stdout` に書き出し
- 認証: 開発用に `X-Profile` の枠のみ（必要に応じて拡張可能）

### 4. CLI（`cmd/mcp-bridge`）

| コマンド | 説明 |
|----------|------|
| `connect` | プロキシ開始。フラグ: `--url`, `--debug` |
| `install` | Claude Desktop 設定の書き換え枠（プレースホルダー） |

### 5. その他

- **client/README.md**: 使い方、`go get` の案内、疎通確認手順
- **.gitignore**: `client/mcp-bridge` バイナリを追加

## アーキテクチャ・ルール準拠

- `.cursor/architecture.md` および `.cursor/rules/client-go.mdc` に準拠
- 標準的な Go レイアウト（`cmd/`, `internal/`）
- Cobra & Viper で CLI／設定を管理
- 並行処理は Goroutine + Channel、Context でキャンセル対応
- panic 禁止、エラーはラップして返却

## 動作確認

- `go run ./cmd/mcp-bridge connect` で待機状態になること
- Kotlin サーバー起動後、以下で疎通可能:
  ```bash
  cd client
  echo '{"jsonrpc":"2.0","method":"initialize","params":{},"id":1}' | go run ./cmd/mcp-bridge connect
  ```
  標準出力に JSON-RPC レスポンスが返れば成功

## 今後の拡張（本 PR の範囲外）

- `install`: Claude Desktop 設定ファイルの自動更新（パス探索・書き換え）
- `login`: AWS Cognito 認証フローとトークン管理
- 認証ヘッダーへのトークン付与
