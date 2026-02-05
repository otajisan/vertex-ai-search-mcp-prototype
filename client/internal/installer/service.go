package installer

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
)

const (
	// ServerKey は claude_desktop_config.json の mcpServers に追加するキー名です。
	ServerKey = "vertex-ai-rag"
)

// Service は Claude Desktop 設定ファイルの更新を行います。
type Service struct {
	// ConfigPath は書き換え対象の設定ファイルの絶対パス。
	// 空の場合は ConfigPathByOS() で決定したパスを使用する。
	ConfigPath string
}

// ConfigPathByOS は runtime.GOOS に応じた Claude Desktop 設定ファイルのパスを返します。
// ファイルが存在しない場合でもパスは返します。呼び出し側で新規作成してください。
// - windows: %APPDATA%\Claude\claude_desktop_config.json
// - darwin (macOS): ~/Library/Application Support/Claude/claude_desktop_config.json
// 上記以外の OS では darwin と同じパスを返します。
// Windows で APPDATA が未設定の場合はエラーを返します。
func ConfigPathByOS() (string, error) {
	switch runtime.GOOS {
	case "windows":
		appdata := os.Getenv("APPDATA")
		if appdata == "" {
			return "", fmt.Errorf("APPDATA が設定されていません。Windows では Claude Desktop 設定のパスを特定できません")
		}
		return filepath.Join(appdata, "Claude", "claude_desktop_config.json"), nil
	default:
		home, err := os.UserHomeDir()
		if err != nil {
			return "", fmt.Errorf("ホームディレクトリの取得に失敗しました: %w", err)
		}
		return filepath.Join(home, "Library", "Application Support", "Claude", "claude_desktop_config.json"), nil
	}
}

// Install は設定ファイルを読み込み、mcpServers に vertex-ai-rag エントリを追加または上書きして保存します。
// serverURL は MCP サーバーの URL（例: http://localhost:8080/sse）、profile は AWS プロファイル名です。
// binaryPath は command に設定する mcp-bridge バイナリの絶対パス（通常は os.Executable() の戻り値）です。
func (s *Service) Install(serverURL, profile, binaryPath string) error {
	configPath := s.ConfigPath
	if configPath == "" {
		p, err := ConfigPathByOS()
		if err != nil {
			return err
		}
		configPath = p
	}

	dir := filepath.Dir(configPath)
	if err := os.MkdirAll(dir, 0700); err != nil {
		return fmt.Errorf("設定ディレクトリの作成に失敗しました (%s): %w", dir, err)
	}

	root, err := s.readConfig(configPath)
	if err != nil {
		return err
	}

	mcpServers, _ := root["mcpServers"].(map[string]interface{})
	if mcpServers == nil {
		mcpServers = make(map[string]interface{})
		root["mcpServers"] = mcpServers
	}

	mcpServers[ServerKey] = map[string]interface{}{
		"command": binaryPath,
		"args":    []interface{}{"connect", "--url", serverURL},
		"env": map[string]interface{}{
			"AWS_PROFILE": profile,
		},
	}

	if err := s.writeConfig(configPath, root); err != nil {
		return err
	}

	return nil
}

func (s *Service) readConfig(path string) (map[string]interface{}, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return make(map[string]interface{}), nil
		}
		if os.IsPermission(err) {
			return nil, fmt.Errorf("設定ファイルの読み取り権限がありません: %s", path)
		}
		return nil, fmt.Errorf("設定ファイルの読み取りに失敗しました: %w", err)
	}

	var root map[string]interface{}
	if len(data) == 0 {
		return make(map[string]interface{}), nil
	}
	if err := json.Unmarshal(data, &root); err != nil {
		return nil, fmt.Errorf("設定ファイルの JSON 解析に失敗しました: %w", err)
	}
	if root == nil {
		root = make(map[string]interface{})
	}
	return root, nil
}

func (s *Service) writeConfig(path string, root map[string]interface{}) error {
	data, err := json.MarshalIndent(root, "", "  ")
	if err != nil {
		return fmt.Errorf("設定の JSON 出力に失敗しました: %w", err)
	}
	if err := os.WriteFile(path, data, 0600); err != nil {
		if os.IsPermission(err) {
			return fmt.Errorf("設定ファイルの書き込み権限がありません: %s", path)
		}
		return fmt.Errorf("設定ファイルの書き込みに失敗しました: %w", err)
	}
	return nil
}
