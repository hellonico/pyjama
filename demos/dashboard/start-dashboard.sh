#!/bin/bash
# Start the Pyjama Agent Dashboard on port 8090

echo "🚀 Starting Pyjama Dashboard on port 8090..."

# Kill any existing dashboard process
lsof -ti:8090 | xargs kill -9 2>/dev/null

# Get the pyjama root directory (two levels up from this script)
PYJAMA_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# Start the dashboard from pyjama root
cd "$PYJAMA_ROOT"
clj -M -e '(require (quote [pyjama.agent.hooks.dashboard :as d])) (d/start-dashboard!) (println "\n✅ Dashboard running on http://localhost:8090\n") (Thread/sleep 300000)'
