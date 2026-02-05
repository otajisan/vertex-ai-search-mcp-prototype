package main

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
)

func main() {
	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

var rootCmd = &cobra.Command{
	Use:   "mcp-bridge",
	Short: "Bridge between Claude Desktop (stdio JSON-RPC) and MCP server (HTTPS/SSE)",
}

func init() {
	rootCmd.AddCommand(connectCmd)
	rootCmd.AddCommand(installCmd)
}
