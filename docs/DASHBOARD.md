# Live Agent Dashboard 📊

Real-time web-based monitoring of your Pyjama agents with interactive Mermaid flowchart visualizations.

## Features

- **📊 Mermaid Diagrams**: Beautiful flowchart visualization of agent workflows
- **⚡ Real-time Highlighting**: Current step glows blue with animation
- **📋 Modal Tabs**: Steps list and interactive diagram views
- **📜 Past Runs**: Separate tracking of active and completed agents
- **📈 Metrics**: Currently running count, success rate, throughput
- **🔄 Auto-refresh**: Updates every 2 seconds
- **🌐 Cross-process**: File-based metrics for multi-process tracking

## Quick Start

### 1. Start the Dashboard

```bash
cd your-pyjama-project
clj -M -m pyjama.agent.hooks.dashboard
```

Dashboard starts on **http://localhost:8090**

### 2. Run Your Agent

In another terminal:

```bash
# Using an alias
clj -M:your-agent

# Or directly
clj -M -m pyjama.cli.agent run your-agent-name
```

### 3. Open in Browser

```bash
open http://localhost:8090
```

## What You'll See

### Agent Cards

Each running agent shows:
- **Agent name** and status badge (RUNNING/COMPLETED)
- **Duration** since start
- **Current step** with progress indicator
- **Step count** (e.g., "5 of 12 steps completed")

Click any card to see full details!

### Modal Views

When you click an agent card, a modal opens with two tabs:

#### 📋 Steps Tab
- Complete list of all steps
- Status indicators: Pending (gray), Running (blue), Completed (green)
- Duration for each completed step
- Running time for active step

#### 📊 Diagram Tab
- Interactive Mermaid flowchart
- **Node types**:
  - Green circle → Agent start
  - Blue rectangle → Tool/action steps
  - Orange hexagon → Loop constructs
  - Diamond → Routing/conditionals
  - Red circle → Done
- **Current step** glows blue with animation
- Edge labels show conditions and transitions

### Metrics Overview

Global metrics card shows:
- **Currently Running**: Active agents count
- **Total Executions**: All-time run count
- **Success Rate**: Percentage of successful completions
- **Avg Duration**: Average agent execution time
- **Throughput**: Operations per second

## Architecture

### Shared Metrics File

Dashboard uses `~/.pyjama/metrics.json` for cross-process state:

```json
{
  "agents": {
    "my-agent": {
      "status": "running",
      "start-time": 1675890123000,
      "current-step": "process-data",
      "steps": [...],
      "spec": {...}
    }
  },
  "metrics": {...},
  "recent-logs": [...]
}
```

### API Endpoints

- `GET /` - Dashboard HTML
- `GET /api/data` - Full dashboard state (JSON)
- `GET /api/agent/{id}/diagram` - Mermaid diagram for specific agent

### Shutdown Hook

Agents automatically register completion on:
- Normal termination
- **Ctrl-C** (via JVM shutdown hook)

## Customization

### Custom Port

```bash
# Start on different port
clj -M -m pyjama.agent.hooks.dashboard 8080
```

Or in code:

```clojure
(require '[pyjama.agent.hooks.dashboard :as dashboard])

(dashboard/start-dashboard! 8080)
; => {:status :ok, :port 8080, :url "http://localhost:8080"}
```

### Clear Metrics

Remove stale agents:

```bash
# Clear all metrics
rm ~/.pyjama/metrics.json

# Or specific agent (using jq)
cat ~/.pyjama/metrics.json | jq 'del(.agents."old-agent")' > /tmp/metrics.json
mv /tmp/metrics.json ~/.pyjama/metrics.json
```

## Troubleshooting

### Agent Not Showing

1. **Check metrics file exists**: `ls -la ~/.pyjama/metrics.json`
2. **Verify agent registration**: Look for log message
   ```
   ✓ Agent 'my-agent' registered with workflow definition for dashboard
   ```
3. **Refresh browser**: Dashboard auto-refreshes, but manual refresh can help

### Diagram Not Rendering

1. **Check spec storage**: Agent must have `:spec` in metrics
2. **View raw diagram**: Visit `/api/agent/your-agent-id/diagram`
3. **Browser console**: Check for Mermaid.js errors

### Dashboard Won't Start

1. **Port already in use**: Try different port or kill existing process
   ```bash
   lsof -i :8090
   kill <PID>
   ```
2. **Check for errors**: Run in foreground to see error messages

## Example: Complete Setup

```bash
# Terminal 1: Dashboard
cd /Users/nico/cool/origami-nightweave/pyjama
clj -M -m pyjama.agent.hooks.dashboard

# Terminal 2: Demo agent
clj -M:loop-demo

# Terminal 3: Your production agent
cd /Users/nico/cool/pyjama-commercial/jetlag
clj -M:jetlag

# Browser
open http://localhost:8090
```

## Screenshots

### Active Agents View
- Clean card-based layout
- Status badges and progress indicators
- Click to expand

### Diagram View
- Full workflow visualization
- Real-time step highlighting
- Smooth animations

## See Also

- [Agent Framework Documentation](LOOP_SUPPORT.md) - How to build agents
- [Examples](EXAMPLES.md) - Agent configuration examples
- [Changelog](CHANGELOG.md#030---2026-02-08) - Dashboard release notes
