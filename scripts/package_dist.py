#!/usr/bin/env python3
"""
配布用パッケージの作成スクリプト。

非エンジニアが簡単にインストールできるよう、
Windows (amd64) と macOS (arm64) 向けのバイナリ・ラッパースクリプト・ZIP を生成する。

前提: Go 1.22+ がインストールされ PATH に含まれていること。
実行: プロジェクトルートで `uv run --project scripts python scripts/package_dist.py`（要: scripts/pyproject.toml）
"""

import logging
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

# プレースホルダーURL（Issue #9 の要件）
INSTALL_URL = "http://localhost:8080/sse"

# プロジェクトルート（このスクリプトは scripts/ に配置）
PROJECT_ROOT = Path(__file__).resolve().parent.parent
CLIENT_DIR = PROJECT_ROOT / "client"
DIST_WIN = PROJECT_ROOT / "dist" / "win"
DIST_MAC = PROJECT_ROOT / "dist" / "mac"
RELEASE_DIR = PROJECT_ROOT / "release"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)


def run_go_build(goos: str, goarch: str, out_path: Path) -> None:
    """指定した GOOS/GOARCH で client/ から Go ビルドを実行する。"""
    env = {
        **os.environ,
        "GOOS": goos,
        "GOARCH": goarch,
        "CGO_ENABLED": "0",
    }
    cmd = ["go", "build", "-o", str(out_path), "./cmd/mcp-bridge"]
    logger.info("実行: GOOS=%s GOARCH=%s go build -o %s", goos, goarch, out_path)
    result = subprocess.run(cmd, cwd=CLIENT_DIR, env=env, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(
            f"ビルド失敗 (GOOS={goos} GOARCH={goarch}): {result.stderr or result.stdout}"
        )


def cross_compile() -> None:
    """client/ で Go をクロスコンパイルし、dist/win と dist/mac にバイナリを出力する。"""
    DIST_WIN.mkdir(parents=True, exist_ok=True)
    DIST_MAC.mkdir(parents=True, exist_ok=True)

    run_go_build("windows", "amd64", DIST_WIN / "mcp-bridge.exe")
    run_go_build("darwin", "arm64", DIST_MAC / "mcp-bridge-mac")
    logger.info("クロスコンパイル完了")


def write_install_bat() -> None:
    """dist/win/install.bat を生成する（mcp-bridge.exe install ... の後に pause）。"""
    path = DIST_WIN / "install.bat"
    content = f"""@echo off
"%~dp0mcp-bridge.exe" install --url {INSTALL_URL}
pause
"""
    path.write_text(content, encoding="utf-8")
    logger.info("作成: %s", path)


def write_install_command() -> None:
    """dist/mac/install.command を生成し、chmod +x を付与する。"""
    path = DIST_MAC / "install.command"
    content = f"""#!/bin/bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
# バイナリに実行権を付与（ZIP 展開で失われる場合があるため）
chmod +x "$DIR/mcp-bridge-mac"
"$DIR/mcp-bridge-mac" install --url {INSTALL_URL}
"""
    path.write_text(content, encoding="utf-8", newline="\n")
    path.chmod(0o755)
    # バイナリにも実行権を付与
    (DIST_MAC / "mcp-bridge-mac").chmod(0o755)
    logger.info("作成: %s (chmod +x 付与済み)", path)


def zip_dir(src_dir: Path, zip_path: Path) -> None:
    """src_dir の中身を zip_path に圧縮する（ディレクトリ自体は含めない）。"""
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for f in src_dir.iterdir():
            zf.write(f, f.name)
    logger.info("作成: %s", zip_path)


def main() -> None:
    """クロスコンパイル → ラッパー生成 → ZIP 出力を行う。"""
    logger.info("配布用パッケージ作成を開始します (project_root=%s)", PROJECT_ROOT)
    if not CLIENT_DIR.is_dir():
        raise FileNotFoundError(f"client ディレクトリが見つかりません: {CLIENT_DIR}")
    if not (CLIENT_DIR / "go.mod").exists():
        raise FileNotFoundError(f"client/go.mod が見つかりません: {CLIENT_DIR / 'go.mod'}")

    cross_compile()
    write_install_bat()
    write_install_command()

    RELEASE_DIR.mkdir(parents=True, exist_ok=True)
    zip_dir(DIST_WIN, RELEASE_DIR / "mcp-tool-windows.zip")
    zip_dir(DIST_MAC, RELEASE_DIR / "mcp-tool-mac.zip")

    logger.info("完了。ZIP は %s に出力しました。", RELEASE_DIR)


if __name__ == "__main__":
    main()
