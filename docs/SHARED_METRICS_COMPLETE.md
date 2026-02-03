# 🎉 Cross-Process Dashboard - COMPLETE!

## What We Built

A **shared metrics system** that allows the dashboard to monitor agents running in **separate processes**!

## The Problem

- Dashboard runs in one JVM process
- Agents run in separate JVM processes  
- They don't share memory/state
- Dashboard couldn't see agent activity ❌

## The Solution ✅

**File-Based Shared Metrics** at `~/.pyjama/metrics.json`

```
┌─────────────────┐         ┌──────────────────────┐
│   Dashboard     │         │  ~/.pyjama/          │
│   Process       │◄────────┤  metrics.json        │
│   (reads)       │         │  (shared file)       │
└─────────────────┘         └──────────────────────┘
                                      ▲
                                      │
                            ┌─────────┴──────────┐
                            │   Agent Process    │
                            │   (writes)         │
                            └────────────────────┘
```

## How It Works

### 1. **Agents Write** to Shared File

When an agent executes a tool:
```clojure
;; Hook registered in codebase-analyzer/init.clj
(shared-metrics-hook {:tool-name :write-file
                      :ctx {:id "software-versions-v2"}
                      :result {:status :ok}})

;; Writes to ~/.pyjama/metrics.json
{:agents {"software-versions-v2" {:status "running" ...}}
 :recent-logs [{:agent-id "software-versions-v2" ...}]
 :global {:count 1 ...}}
```

### 2. **Dashboard Reads** from Shared File

Every 2 seconds, the dashboard:
```clojure
;; dashboard.clj
(defn- get-dashboard-data []
  (shared/get-dashboard-data))  ;; Reads ~/.pyjama/metrics.json

;; Returns data to browser
{:agents {...}
 :metrics {...}
 :recent-logs [...]}
```

### 3. **Browser Displays** Real-Time Updates

The UI auto-refreshes and shows:
- Active agents from all processes
- Aggregated metrics
- Combined activity logs

## Files Created/Modified

### New Files
- ✅ `pyjama/src/pyjama/agent/hooks/shared_metrics.clj` - Shared metrics system
- ✅ `pyjama/test-shared-metrics.sh` - Test script

### Modified Files
- ✅ `pyjama/src/pyjama/agent/hooks/dashboard.clj` - Read from shared metrics
- ✅ `codebase-analyzer/src/codebase_analyzer/init.clj` - Write to shared metrics

## Features

### Thread-Safe File Locking
```clojure
(defn- with-file-lock [f]
  (let [lock (File. lock-file)]
    (while (not (.createNewFile lock))
      (Thread/sleep 10))
    (try (f)
      (finally (.delete lock)))))
```

### Automatic Agent Tracking
```clojure
(record-agent-start! "software-versions-v2")
(record-agent-activity! {:agent-id "..." :tool-name :write-file})
(record-agent-complete! "software-versions-v2")
```

### Metrics Aggregation
```clojure
(record-tool-execution! {:tool-name :write-file
                        :status :ok
                        :duration-ms 45.2})
```

### Dashboard Data Format
```json
{
  "agents": {
    "software-versions-v2": {
      "status": "running",
      "start-time": 1706945873123,
      "last-seen": 1706945875456
    }
  },
  "metrics": {
    "global": {
      "count": 156,
      "success": 152,
      "success-rate": 0.974
    }
  },
  "recent-logs": [
    {
      "timestamp": 1706945873123,
      "agent-id": "software-versions-v2",
      "tool": "write-file",
      "status": "ok"
    }
  ]
}
```

## Testing

### Quick Test
```bash
cd /Users/nico/cool/origami-nightweave/pyjama
./test-shared-metrics.sh
```

This will:
1. Start the dashboard on port 8080
2. Run an agent in a separate process
3. Show real-time updates in the dashboard!

### Manual Test

```bash
# Terminal 1: Start dashboard
cd /Users/nico/cool/origami-nightweave/pyjama
clj -M -e '
(require (quote [pyjama.agent.hooks.dashboard :as d]))
(d/start-dashboard! 8080)
(Thread/sleep 300000)
'

# Terminal 2: Run agent
cd /Users/nico/cool/pyjama-commercial/codebase-analyzer
clj -M:pyjama run software-versions-v2 '{"project-dir":"."}'

# Terminal 3: Watch the metrics file
watch -n 1 'cat ~/.pyjama/metrics.json | jq .'
```

### Check Shared Metrics File

```bash
# View the shared metrics
cat ~/.pyjama/metrics.json | jq .

# Clear metrics
rm ~/.pyjama/metrics.json
```

## Benefits

✅ **Cross-Process Monitoring** - Dashboard sees all agents  
✅ **Real-Time Updates** - 2-second refresh  
✅ **Thread-Safe** - File locking prevents corruption  
✅ **Persistent** - Metrics survive process restarts  
✅ **Aggregated** - Combined view of all activity  
✅ **Zero Config** - Works automatically  

## Architecture

### Process Isolation
```
Process 1 (Dashboard)          Process 2 (Agent)
┌─────────────────┐           ┌──────────────────┐
│  Dashboard      │           │  Agent           │
│  - Reads every  │           │  - Writes on     │
│    2 seconds    │           │    tool exec     │
│  - Displays UI  │           │  - Updates state │
└────────┬────────┘           └────────┬─────────┘
         │                             │
         └──────────┬──────────────────┘
                    ▼
         ┌─────────────────────┐
         │  ~/.pyjama/         │
         │  metrics.json       │
         │  (shared file)      │
         └─────────────────────┘
```

### Data Flow
```
Agent Execution
      │
      ▼
Tool Hook Triggered
      │
      ▼
shared-metrics-hook
      │
      ▼
record-agent-activity!
      │
      ▼
update-metrics! (with file lock)
      │
      ▼
Write to ~/.pyjama/metrics.json
      │
      ▼
Dashboard reads file (every 2s)
      │
      ▼
Browser displays updates
```

## API

### Writing to Shared Metrics

```clojure
(require '[pyjama.agent.hooks.shared-metrics :as shared])

;; Record agent start
(shared/record-agent-start! "my-agent")

;; Record activity
(shared/record-agent-activity! {:agent-id "my-agent"
                                :tool-name :write-file
                                :status :ok})

;; Record completion
(shared/record-agent-complete! "my-agent")

;; Record tool execution metrics
(shared/record-tool-execution! {:tool-name :write-file
                                :agent-id "my-agent"
                                :status :ok
                                :duration-ms 45.2})
```

### Reading from Shared Metrics

```clojure
(require '[pyjama.agent.hooks.shared-metrics :as shared])

;; Get all dashboard data
(shared/get-dashboard-data)

;; Read raw metrics
(shared/read-metrics)

;; Clear all metrics
(shared/clear-metrics!)
```

### Hook Integration

```clojure
(require '[pyjama.agent.hooks :as hooks]
         '[pyjama.agent.hooks.shared-metrics :as shared])

;; Register the shared metrics hook
(hooks/register-hook! :write-file shared/shared-metrics-hook)
```

## Troubleshooting

### Dashboard shows no agents

1. Check if metrics file exists:
   ```bash
   ls -la ~/.pyjama/metrics.json
   ```

2. Check if agent is writing:
   ```bash
   watch -n 1 'cat ~/.pyjama/metrics.json | jq .'
   ```

3. Verify hook is registered:
   ```clojure
   (require '[pyjama.agent.hooks :as hooks])
   (count (hooks/get-hooks :write-file))  ;; Should be > 0
   ```

### File lock issues

If you see lock file stuck:
```bash
rm ~/.pyjama/metrics.lock
```

### Clear all metrics

```bash
rm -rf ~/.pyjama/
```

## Summary

You asked for **cross-process data collection** and you got:

✅ **File-based shared metrics** at `~/.pyjama/metrics.json`  
✅ **Thread-safe file locking** to prevent corruption  
✅ **Automatic agent tracking** via hooks  
✅ **Real-time dashboard updates** every 2 seconds  
✅ **Aggregated metrics** from all processes  
✅ **Zero configuration** - works automatically  
✅ **Complete API** for reading/writing  
✅ **Test scripts** to verify it works  

**Now the dashboard can monitor agents running in any process!** 🎉✨

---

## Next Steps

1. **Test it**: Run `./test-shared-metrics.sh`
2. **Open dashboard**: http://localhost:8080
3. **Run agents**: They'll appear automatically!
4. **Watch magic**: Real-time cross-process monitoring! ✨
