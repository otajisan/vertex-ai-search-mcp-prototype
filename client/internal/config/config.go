package config

import (
	"fmt"
	"net/url"

	"github.com/spf13/viper"
)

// DefaultSSEURL はデフォルトのSSEエンドポイントURLです。
const DefaultSSEURL = "http://localhost:8080/sse"

// Config は接続先や認証プロファイルなどの設定を保持します。
type Config struct {
	// URL はMCPサーバーのSSEエンドポイントURL（例: http://localhost:8080/sse）
	URL string
	// Profile は認証プロファイル名（将来のCognito用、今回は未使用）
	Profile string
	// Debug はデバッグログを有効にするか
	Debug bool
}

// Load はviperから設定を読み込み、Configを返します。
// フラグや環境変数で上書き可能です。
func Load() (*Config, error) {
	v := viper.New()

	v.SetDefault("url", DefaultSSEURL)
	v.SetDefault("profile", "")
	v.SetDefault("debug", false)

	// 環境変数: MCP_BRIDGE_URL, MCP_BRIDGE_PROFILE, MCP_BRIDGE_DEBUG
	v.SetEnvPrefix("MCP_BRIDGE")
	v.AutomaticEnv()

	// 設定ファイル（任意）: .mcp-bridge.yaml など
	v.SetConfigName(".mcp-bridge")
	v.SetConfigType("yaml")
	v.AddConfigPath(".")
	v.AddConfigPath("$HOME")
	_ = v.ReadInConfig() // ファイルがなくても続行

	cfg := &Config{
		URL:     v.GetString("url"),
		Profile: v.GetString("profile"),
		Debug:   v.GetBool("debug"),
	}

	if err := cfg.Validate(); err != nil {
		return nil, err
	}

	return cfg, nil
}

// Validate は設定の妥当性を検証します。
func (c *Config) Validate() error {
	if c.URL == "" {
		return fmt.Errorf("url must not be empty")
	}
	u, err := url.Parse(c.URL)
	if err != nil {
		return fmt.Errorf("invalid url: %w", err)
	}
	if u.Scheme != "http" && u.Scheme != "https" {
		return fmt.Errorf("url scheme must be http or https, got %q", u.Scheme)
	}
	return nil
}

// BaseURL はSSE URLからベースURL（スキーム＋ホスト）を返します。
// 例: http://localhost:8080/sse -> http://localhost:8080
func (c *Config) BaseURL() string {
	u, _ := url.Parse(c.URL)
	u.Path = ""
	u.RawPath = ""
	u.RawQuery = ""
	u.Fragment = ""
	return u.String()
}

// McpPath はPOST先のJSON-RPCパスを返します。サーバーがSSEで通知する場合はそちらを優先する想定。
// デフォルトは /mcp です。
func (c *Config) McpPath() string {
	return "/mcp"
}
