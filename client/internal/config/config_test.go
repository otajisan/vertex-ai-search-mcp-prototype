package config

import (
	"testing"
)

func TestConfig_Validate(t *testing.T) {
	tests := []struct {
		name    string
		url     string
		wantErr bool
	}{
		{"empty url", "", true},
		{"invalid url", "://broken", true},
		{"invalid scheme", "ftp://localhost/sse", true},
		{"http ok", "http://localhost:8080/sse", false},
		{"https ok", "https://api.example.com/sse", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cfg := &Config{URL: tt.url}
			err := cfg.Validate()
			if (err != nil) != tt.wantErr {
				t.Errorf("Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestConfig_BaseURL(t *testing.T) {
	tests := []struct {
		url  string
		want string
	}{
		{"http://localhost:8080/sse", "http://localhost:8080"},
		{"https://api.example.com/sse?foo=1", "https://api.example.com"},
	}
	for _, tt := range tests {
		t.Run(tt.url, func(t *testing.T) {
			cfg := &Config{URL: tt.url}
			got := cfg.BaseURL()
			if got != tt.want {
				t.Errorf("BaseURL() = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestConfig_McpPath(t *testing.T) {
	cfg := &Config{}
	if got := cfg.McpPath(); got != "/mcp" {
		t.Errorf("McpPath() = %q, want /mcp", got)
	}
}
