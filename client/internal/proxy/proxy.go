package proxy

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"sync"
	"time"

	"github.com/otajisan/vertex-ai-search-mcp-prototype/client/internal/config"
)

// Proxy はstdioとMCPサーバー（SSE + POST）の間でJSON-RPCを中継します。
type Proxy struct {
	cfg    *config.Config
	client *http.Client
}

// New はProxyを生成します。
func New(cfg *config.Config) *Proxy {
	return &Proxy{
		cfg: cfg,
		client: &http.Client{
			Timeout:   0, // POSTはストリームなのでタイムアウトなし
			Transport: &http.Transport{},
		},
	}
}

// Run はプロキシを開始します。
// Goroutine A: stdin から JSON-RPC を読み、サーバーへ POST し、レスポンスを stdout 用チャネルへ送る。
// Goroutine B: GET /sse でイベントを受信し、イベントデータを stdout 用チャネルへ送る。
// 単一の writer がチャネルから取り出して os.Stdout に書き込みます。
func (p *Proxy) Run(ctx context.Context) error {
	baseURL := p.cfg.BaseURL()
	mcpURL := baseURL + p.cfg.McpPath()
	sseURL := p.cfg.URL

	toStdout := make(chan []byte, 32)
	var wg sync.WaitGroup

	// Goroutine A: stdin → POST /mcp → toStdout
	wg.Add(1)
	go func() {
		defer wg.Done()
		p.runStdinToPost(ctx, mcpURL, toStdout)
	}()

	// Goroutine B: GET /sse → event data → toStdout
	wg.Add(1)
	go func() {
		defer wg.Done()
		p.runSSEReceiver(ctx, sseURL, toStdout)
	}()

	// stdout writer: toStdout を os.Stdout に書き込む
	go func() {
		for b := range toStdout {
			if _, err := os.Stdout.Write(b); err != nil {
				if p.cfg.Debug {
					fmt.Fprintf(os.Stderr, "[proxy] stdout write error: %v\n", err)
				}
				return
			}
			if len(b) > 0 && b[len(b)-1] != '\n' {
				os.Stdout.Write([]byte("\n"))
			}
		}
	}()

	// 両方のgoroutineが終了したら toStdout を閉じる
	go func() {
		wg.Wait()
		close(toStdout)
	}()

	<-ctx.Done()
	return ctx.Err()
}

// runStdinToPost は標準入力から JSON-RPC を1行ずつ読み、サーバーに POST し、レスポンスを ch に送ります。
func (p *Proxy) runStdinToPost(ctx context.Context, mcpURL string, ch chan<- []byte) {
	scanner := bufio.NewScanner(os.Stdin)
	scanner.Buffer(nil, 1024*1024) // 1MB max per line

	for scanner.Scan() {
		line := scanner.Bytes()
		if len(line) == 0 {
			continue
		}

		// 空でない行をそのまま JSON-RPC リクエストとして送る
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, mcpURL, bytes.NewReader(line))
		if err != nil {
			p.sendError(ch, "build request", err)
			continue
		}
		req.Header.Set("Content-Type", "application/json")
		p.addAuthHeader(req)

		resp, err := p.client.Do(req)
		if err != nil {
			p.sendError(ch, "post request", err)
			continue
		}

		body, err := io.ReadAll(resp.Body)
		resp.Body.Close()
		if err != nil {
			p.sendError(ch, "read response", err)
			continue
		}

		if resp.StatusCode != http.StatusOK {
			if p.cfg.Debug {
				fmt.Fprintf(os.Stderr, "[proxy] POST %s status=%d body=%s\n", mcpURL, resp.StatusCode, string(body))
			}
			p.sendError(ch, "server error", fmt.Errorf("status %d: %s", resp.StatusCode, string(body)))
			continue
		}

		select {
		case ch <- body:
		case <-ctx.Done():
			return
		}
	}

	if err := scanner.Err(); err != nil && err != io.EOF && p.cfg.Debug {
		fmt.Fprintf(os.Stderr, "[proxy] stdin scan error: %v\n", err)
	}
}

// runSSEReceiver は GET /sse でストリームを受け、各イベントの data を ch に送ります。
func (p *Proxy) runSSEReceiver(ctx context.Context, sseURL string, ch chan<- []byte) {
	for {
		select {
		case <-ctx.Done():
			return
		default:
		}

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, sseURL, nil)
		if err != nil {
			if p.cfg.Debug {
				fmt.Fprintf(os.Stderr, "[proxy] SSE request build error: %v\n", err)
			}
			time.Sleep(2 * time.Second)
			continue
		}
		req.Header.Set("Accept", "text/event-stream")
		p.addAuthHeader(req)

		resp, err := p.client.Do(req)
		if err != nil {
			if p.cfg.Debug {
				fmt.Fprintf(os.Stderr, "[proxy] SSE request error: %v\n", err)
			}
			time.Sleep(2 * time.Second)
			continue
		}

		if resp.StatusCode != http.StatusOK {
			resp.Body.Close()
			if p.cfg.Debug {
				fmt.Fprintf(os.Stderr, "[proxy] SSE status=%d\n", resp.StatusCode)
			}
			time.Sleep(2 * time.Second)
			continue
		}

		p.readSSEStream(ctx, resp.Body, ch)
		resp.Body.Close()
	}
}

// readSSEStream は SSE ストリームをパースし、JSON-RPC レスポンスと思われる data のみ ch に送ります。
// サーバーが送る endpoint 通知（例: {"url":"/mcp"}）は stdout に転送せず、プロトコル上は POST のレスポンスのみを stdout に返す想定です。
func (p *Proxy) readSSEStream(ctx context.Context, r io.Reader, ch chan<- []byte) {
	scanner := bufio.NewScanner(r)
	scanner.Buffer(nil, 1024*1024)

	var currentData []byte
	for scanner.Scan() {
		select {
		case <-ctx.Done():
			return
		default:
		}

		line := scanner.Bytes()
		if len(line) == 0 {
			if len(currentData) > 0 {
				if isJSONRPCResponse(currentData) {
					cp := make([]byte, len(currentData))
					copy(cp, currentData)
					select {
					case ch <- cp:
					case <-ctx.Done():
						return
					}
				}
				currentData = nil
			}
			continue
		}

		if bytes.HasPrefix(line, []byte("data:")) {
			data := bytes.TrimSpace(line[5:])
			if len(data) > 0 {
				currentData = append(currentData, data...)
			}
		}
	}
}

// isJSONRPCResponse は data が JSON-RPC レスポンス（result または error を持つ）かどうかを判定します。
// endpoint 通知 {"url":"/mcp"} は転送しないため false を返します。
func isJSONRPCResponse(data []byte) bool {
	var m map[string]json.RawMessage
	if err := json.Unmarshal(data, &m); err != nil {
		return false
	}
	_, hasResult := m["result"]
	_, hasError := m["error"]
	return hasResult || hasError
}

func (p *Proxy) addAuthHeader(req *http.Request) {
	// 開発用: プロファイルやトークンがあれば付与。今回はダミーまたは未使用。
	if p.cfg.Profile != "" && p.cfg.Debug {
		req.Header.Set("X-Profile", p.cfg.Profile)
	}
}

func (p *Proxy) sendError(ch chan<- []byte, msg string, err error) {
	errResp := map[string]any{
		"jsonrpc": "2.0",
		"error": map[string]any{
			"code":    -32603,
			"message": fmt.Sprintf("%s: %v", msg, err),
		},
		"id": nil,
	}
	body, _ := json.Marshal(errResp)
	select {
	case ch <- body:
	default:
		if p.cfg.Debug {
			fmt.Fprintf(os.Stderr, "[proxy] error (channel full): %s: %v\n", msg, err)
		}
	}
}
