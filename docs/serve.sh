#!/usr/bin/env bash
# Serves the docs site locally for testing
PORT="${1:-8888}"
echo "Serving docs at http://localhost:$PORT"
echo "Press Ctrl+C to stop"
cd "$(dirname "$0")" && python3 -m http.server "$PORT"
