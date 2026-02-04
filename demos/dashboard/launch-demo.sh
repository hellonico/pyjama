#!/bin/bash
# Dashboard Demo - Launch multiple agents to showcase the dashboard
#
# This script launches 5 small agents in parallel to demonstrate
# the Pyjama dashboard's ability to monitor multiple concurrent agents.
#
# Usage: ./launch-demo.sh
#
# Then open http://localhost:8090 to see the dashboard!

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENTS_DIR="$SCRIPT_DIR/agents"
OUTPUT_DIR="$SCRIPT_DIR/output"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                                ║${NC}"
echo -e "${BLUE}║              🤖 PYJAMA DASHBOARD DEMO 🤖                       ║${NC}"
echo -e "${BLUE}║                                                                ║${NC}"
echo -e "${BLUE}║         Launching multiple agents in parallel...              ║${NC}"
echo -e "${BLUE}║                                                                ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Clean up any previous runs
rm -f "$OUTPUT_DIR"/*.txt "$OUTPUT_DIR"/*.py "$OUTPUT_DIR"/*.md

echo -e "${YELLOW}📊 Dashboard available at: http://localhost:8090${NC}"
echo ""
echo -e "${GREEN}🚀 Launching agents...${NC}"
echo ""

# Launch agents in parallel
cd "$SCRIPT_DIR/../.."

# Agent 1: Hello World
echo "  ✓ Starting hello-world agent..."
clj -J-Dagents.edn=demos/dashboard/agents -M:pyjama run demos/dashboard/agents/hello-world.edn "{\"output-dir\": \"$OUTPUT_DIR\"}" > "$OUTPUT_DIR/hello-world.log" 2>&1 &
AGENT1_PID=$!

# Small delay to stagger starts
sleep 0.5

# Agent 2: File Counter
echo "  ✓ Starting file-counter agent..."
clj -J-Dagents.edn=demos/dashboard/agents -M:pyjama run demos/dashboard/agents/file-counter.edn "{\"output-dir\": \"$OUTPUT_DIR\"}" > "$OUTPUT_DIR/file-counter.log" 2>&1 &
AGENT2_PID=$!

sleep 0.5

# Agent 3: Poem Writer
echo "  ✓ Starting poem-writer agent..."
clj -J-Dagents.edn=demos/dashboard/agents -M:pyjama run demos/dashboard/agents/poem-writer.edn "{\"output-dir\": \"$OUTPUT_DIR\"}" > "$OUTPUT_DIR/poem-writer.log" 2>&1 &
AGENT3_PID=$!

sleep 0.5

# Agent 4: Quick Math
echo "  ✓ Starting quick-math agent..."
clj -J-Dagents.edn=demos/dashboard/agents -M:pyjama run demos/dashboard/agents/quick-math.edn "{\"output-dir\": \"$OUTPUT_DIR\"}" > "$OUTPUT_DIR/quick-math.log" 2>&1 &
AGENT4_PID=$!

sleep 0.5

# Agent 5: Code Snippet
echo "  ✓ Starting code-snippet agent..."
clj -J-Dagents.edn=demos/dashboard/agents -M:pyjama run demos/dashboard/agents/code-snippet.edn "{\"output-dir\": \"$OUTPUT_DIR\"}" > "$OUTPUT_DIR/code-snippet.log" 2>&1 &
AGENT5_PID=$!

echo ""
echo -e "${GREEN}✅ All agents launched!${NC}"
echo ""
echo -e "${YELLOW}📊 Open the dashboard to watch them run:${NC}"
echo -e "${BLUE}   http://localhost:8090${NC}"
echo ""
echo -e "${YELLOW}💡 Tips:${NC}"
echo "   • Click on the '🤖 Active Agents' tab to see running agents"
echo "   • Click any agent card to see its full workflow"
echo "   • Check the '📝 Activity' tab for real-time tool executions"
echo "   • The '📊 Metrics' tab shows overall performance stats"
echo ""
echo -e "${YELLOW}⏳ Waiting for agents to complete...${NC}"
echo ""

# Wait for all agents to complete
wait $AGENT1_PID 2>/dev/null
echo "  ✓ hello-world completed"

wait $AGENT2_PID 2>/dev/null
echo "  ✓ file-counter completed"

wait $AGENT3_PID 2>/dev/null
echo "  ✓ poem-writer completed"

wait $AGENT4_PID 2>/dev/null
echo "  ✓ quick-math completed"

wait $AGENT5_PID 2>/dev/null
echo "  ✓ code-snippet completed"

echo ""
echo -e "${GREEN}🎉 All agents completed successfully!${NC}"
echo ""
echo -e "${YELLOW}📁 Output files saved to:${NC}"
echo "   $OUTPUT_DIR"
echo ""
echo -e "${YELLOW}📋 Agent logs saved to:${NC}"
echo "   $OUTPUT_DIR/*.log"
echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                                ║${NC}"
echo -e "${BLUE}║              ✨ Demo Complete! ✨                              ║${NC}"
echo -e "${BLUE}║                                                                ║${NC}"
echo -e "${BLUE}║  The dashboard will continue to show the completed agents.    ║${NC}"
echo -e "${BLUE}║  Refresh the page to see the final metrics!                   ║${NC}"
echo -e "${BLUE}║                                                                ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""
