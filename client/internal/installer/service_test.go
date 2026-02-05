package installer

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestService_Install(t *testing.T) {
	dir := t.TempDir()
	configPath := filepath.Join(dir, "claude_desktop_config.json")
	binaryPath := filepath.Join(dir, "mcp-bridge")

	tests := []struct {
		name       string
		initial    string
		url        string
		profile    string
		wantKey    string
		wantCmd    string
		wantArgs   []interface{}
		wantEnv    map[string]interface{}
		preserve   string // このキーが既存 JSON にあった場合、残っていることを確認する
	}{
		{
			name:     "empty file creates mcpServers and vertex-ai-rag",
			initial:  "",
			url:      "http://localhost:8080/sse",
			profile:  "default",
			wantKey:  ServerKey,
			wantCmd:  binaryPath,
			wantArgs: []interface{}{"connect", "--url", "http://localhost:8080/sse"},
			wantEnv:  map[string]interface{}{"AWS_PROFILE": "default"},
		},
		{
			name:     "existing mcpServers merged",
			initial:  `{"mcpServers":{"other":{"command":"other"}}}`,
			url:      "http://example.com/sse",
			profile:  "myprofile",
			wantKey:  ServerKey,
			wantCmd:  binaryPath,
			wantArgs: []interface{}{"connect", "--url", "http://example.com/sse"},
			wantEnv:  map[string]interface{}{"AWS_PROFILE": "myprofile"},
			preserve: "other",
		},
		{
			name:     "overwrites existing vertex-ai-rag",
			initial:  `{"mcpServers":{"vertex-ai-rag":{"command":"old"}}}`,
			url:      "http://new:9090/sse",
			profile:  "default",
			wantKey:  ServerKey,
			wantCmd:  binaryPath,
			wantArgs: []interface{}{"connect", "--url", "http://new:9090/sse"},
			wantEnv:  map[string]interface{}{"AWS_PROFILE": "default"},
		},
		{
			name:     "preserves top-level keys",
			initial:  `{"theme":"dark"}`,
			url:      "http://localhost:8080/sse",
			profile:  "default",
			wantKey:  ServerKey,
			wantCmd:  binaryPath,
			wantArgs: []interface{}{"connect", "--url", "http://localhost:8080/sse"},
			wantEnv:  map[string]interface{}{"AWS_PROFILE": "default"},
			preserve: "theme",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if tt.initial != "" {
				if err := os.WriteFile(configPath, []byte(tt.initial), 0600); err != nil {
					t.Fatalf("write initial: %v", err)
				}
			}

			svc := &Service{ConfigPath: configPath}
			if err := svc.Install(tt.url, tt.profile, binaryPath); err != nil {
				t.Fatalf("Install() error = %v", err)
			}

			data, err := os.ReadFile(configPath)
			if err != nil {
				t.Fatalf("read config: %v", err)
			}
			var root map[string]interface{}
			if err := json.Unmarshal(data, &root); err != nil {
				t.Fatalf("parse config: %v", err)
			}

			mcp, ok := root["mcpServers"].(map[string]interface{})
			if !ok || mcp == nil {
				t.Fatal("mcpServers missing or not object")
			}
			ent, ok := mcp[tt.wantKey].(map[string]interface{})
			if !ok || ent == nil {
				t.Fatalf("mcpServers[%q] missing or not object", tt.wantKey)
			}
			if cmd, _ := ent["command"].(string); cmd != tt.wantCmd {
				t.Errorf("command = %q, want %q", cmd, tt.wantCmd)
			}
			args, _ := ent["args"].([]interface{})
			if len(args) != len(tt.wantArgs) {
				t.Errorf("args len = %d, want %d", len(args), len(tt.wantArgs))
			} else {
				for i := range args {
					if args[i] != tt.wantArgs[i] {
						t.Errorf("args[%d] = %v, want %v", i, args[i], tt.wantArgs[i])
					}
				}
			}
			env, _ := ent["env"].(map[string]interface{})
			for k, v := range tt.wantEnv {
				if env[k] != v {
					t.Errorf("env[%q] = %v, want %v", k, env[k], v)
				}
			}

			if tt.preserve != "" {
				if _, inMcp := mcp[tt.preserve]; inMcp {
					// preserved inside mcpServers
					return
				}
				if _, atRoot := root[tt.preserve]; atRoot {
					return
				}
				t.Errorf("preserved key %q not found in output", tt.preserve)
			}
		})
	}
}

func TestService_Install_createsDir(t *testing.T) {
	dir := t.TempDir()
	configPath := filepath.Join(dir, "sub", "claude_desktop_config.json")
	binaryPath := filepath.Join(dir, "mcp-bridge")

	svc := &Service{ConfigPath: configPath}
	if err := svc.Install("http://localhost:8080/sse", "default", binaryPath); err != nil {
		t.Fatalf("Install() error = %v", err)
	}
	if _, err := os.Stat(configPath); err != nil {
		t.Fatalf("config file not created: %v", err)
	}
}

func TestService_Install_invalidJSON(t *testing.T) {
	dir := t.TempDir()
	configPath := filepath.Join(dir, "claude_desktop_config.json")
	if err := os.WriteFile(configPath, []byte(`{invalid`), 0600); err != nil {
		t.Fatal(err)
	}
	svc := &Service{ConfigPath: configPath}
	err := svc.Install("http://localhost:8080/sse", "default", filepath.Join(dir, "mcp-bridge"))
	if err == nil {
		t.Fatal("Install() expected error for invalid JSON")
	}
}

func TestConfigPathByOS(t *testing.T) {
	path, err := ConfigPathByOS()
	if err != nil {
		t.Fatalf("ConfigPathByOS() error = %v", err)
	}
	if path == "" {
		t.Fatal("ConfigPathByOS() returned empty path")
	}
	switch runtime.GOOS {
	case "windows":
		if filepath.Base(path) != "claude_desktop_config.json" {
			t.Errorf("path = %q, want filename claude_desktop_config.json", path)
		}
	default:
		if filepath.Base(path) != "claude_desktop_config.json" {
			t.Errorf("path = %q, want filename claude_desktop_config.json", path)
		}
		// darwin: .../Library/Application Support/Claude/...
		if !strings.Contains(path, "Claude") {
			t.Errorf("path should contain Claude: %q", path)
		}
	}
}
