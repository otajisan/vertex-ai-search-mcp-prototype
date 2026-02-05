package main

import (
	"fmt"

	"github.com/spf13/cobra"
)

var installCmd = &cobra.Command{
	Use:   "install",
	Short: "Install or update Claude Desktop config to use mcp-bridge (placeholder)",
	Long:  "This command will locate Claude Desktop config file and add mcp-bridge as an MCP server. Not yet implemented.",
	RunE:  runInstall,
}

func runInstall(_ *cobra.Command, _ []string) error {
	// プレースホルダー: 将来、internal/installer で実装する
	// - runtime.GOOS で macOS / Windows を判別
	// - macOS: ~/Library/Application Support/Claude/claude_desktop_config.json
	// - Windows: %APPDATA%\Claude\claude_desktop_config.json
	fmt.Println("install: placeholder — Claude Desktop config update will be implemented here.")
	return nil
}
