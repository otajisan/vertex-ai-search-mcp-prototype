package main

import (
	"fmt"
	"os"

	"github.com/otajisan/vertex-ai-search-mcp-prototype/client/internal/config"
	"github.com/otajisan/vertex-ai-search-mcp-prototype/client/internal/installer"
	"github.com/spf13/cobra"
)

var (
	installURL     string
	installProfile string
)

var installCmd = &cobra.Command{
	Use:   "install",
	Short: "Install or update Claude Desktop config to use mcp-bridge",
	Long:  "Updates claude_desktop_config.json to register this MCP server. Creates the config file and directory if they do not exist.",
	RunE:  runInstall,
}

func init() {
	installCmd.Flags().StringVar(&installURL, "url", config.DefaultSSEURL, "MCP server URL (e.g. http://localhost:8080/sse)")
	installCmd.Flags().StringVar(&installProfile, "profile", "default", "AWS profile name to inject into Claude Desktop env")
}

func runInstall(_ *cobra.Command, _ []string) error {
	binaryPath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("実行バイナリのパス取得に失敗しました: %w", err)
	}

	svc := &installer.Service{}
	if err := svc.Install(installURL, installProfile, binaryPath); err != nil {
		return err
	}

	fmt.Println("設定を更新しました。Claude Desktop を再起動してください。")
	return nil
}
