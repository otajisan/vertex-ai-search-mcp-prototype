package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"github.com/otajisan/vertex-ai-search-mcp-prototype/client/internal/config"
	"github.com/otajisan/vertex-ai-search-mcp-prototype/client/internal/proxy"
	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var (
	connectURL   string
	connectDebug bool
)

var connectCmd = &cobra.Command{
	Use:   "connect",
	Short: "Start the proxy (stdio <-> MCP server over SSE)",
	RunE:  runConnect,
}

func init() {
	connectCmd.Flags().StringVar(&connectURL, "url", config.DefaultSSEURL, "MCP server SSE endpoint URL (e.g. http://localhost:8080/sse)")
	connectCmd.Flags().BoolVar(&connectDebug, "debug", false, "Enable debug logging to stderr")
	_ = viper.BindPFlag("url", connectCmd.Flags().Lookup("url"))
	_ = viper.BindPFlag("debug", connectCmd.Flags().Lookup("debug"))
}

func runConnect(_ *cobra.Command, _ []string) error {
	cfg, err := config.Load()
	if err != nil {
		return fmt.Errorf("load config: %w", err)
	}

	// フラグで上書き（viper は BindPFlag でフラグと紐づいている）
	if v := viper.GetString("url"); v != "" {
		cfg.URL = v
	}
	cfg.Debug = viper.GetBool("debug")

	if err := cfg.Validate(); err != nil {
		return err
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigCh
		cancel()
	}()

	prx := proxy.New(cfg)
	if err := prx.Run(ctx); err != nil && err != context.Canceled {
		return fmt.Errorf("proxy: %w", err)
	}
	return nil
}
