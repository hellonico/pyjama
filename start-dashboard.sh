#!/bin/bash
# Start the Pyjama Agent Dashboard on port 8090

echo "🚀 Starting Pyjama Dashboard on port 8090..."

# Kill any existing dashboard process
lsof -ti:8090 | xargs kill -9 2>/dev/null

# Start the dashboard
cd "$(dirname "$0")"
clj -M -e '(require (quote [pyjama.agent.hooks.dashboard :as d])) (d/start-dashboard!) (println "\n✅ Dashboard running on http://localhost:8090\n") (Thread/sleep 300000)'
