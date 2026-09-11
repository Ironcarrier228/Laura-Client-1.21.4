#!/usr/bin/env bash
# Laura Launcher - one-click starter for Linux/macOS.
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v node >/dev/null 2>&1; then
  echo "[Laura] Node.js не найден. Установите Node.js 18+ LTS: https://nodejs.org/"
  exit 1
fi

if [ ! -d "node_modules/electron" ]; then
  echo "[Laura] Первый запуск: ставлю зависимости..."
  npm install
fi

npm start
