# 🎯 Real-Time Agent Dashboard

## 🎉 Overview

The **Pyjama Agent Dashboard** is a beautiful, real-time web UI that shows:

- 🤖 **Active Agents** - All currently running agents
- 📊 **Live Metrics** - Execution counts, success rates, performance
- 📝 **Recent Activity** - Real-time log stream
- 🔧 **Hook Status** - Registered hooks and their counts
- ⚡ **Auto-Refresh** - Updates every 2 seconds

## 🚀 Quick Start

### Start the Dashboard

```clojure
(require '[pyjama.agent.hooks.dashboard :as dashboard])

;; Start on port 8080
(dashboard/start-dashboard! 8080)

;; Open http://localhost:8080 in your browser
```

### Run Some Agents

```bash
# In another terminal
clj -M:pyjama run software-versions-v2 '{"project-dir":"."}'
```

### Watch the Magic! ✨

The dashboard will show:
- Agent appearing in "Active Agents"
- Metrics updating in real-time
- Logs streaming as tools execute
- Hook counts and status

### Stop the Dashboard

```clojure
(dashboard/stop-dashboard!)
```

## 📊 Dashboard Features

### 1. **Global Metrics**
- Total executions
- Success rate percentage
- Average duration
- Throughput (ops/sec)

### 2. **Active Agents**
- Agent names and IDs
- Running/Completed status
- Execution duration
- Color-coded status badges

### 3. **Registered Hooks**
- Hook counts per tool
- Visual badges showing activity
- Real-time updates

### 4. **Recent Activity**
- Last 20 log entries
- Timestamp, agent, tool, status
- Color-coded success/error
- Auto-scrolling feed

## 🎨 Beautiful UI

The dashboard features:
- 🌈 Gradient purple theme
- 💫 Smooth animations
- 📱 Responsive design
- ✨ Pulsing live indicator
- 🎯 Clean, modern interface

## 🔧 Configuration

### Custom Port

```clojure
;; Start on port 3000
(dashboard/start-dashboard! 3000)
```

### Integration with Init

```clojure
(ns my-project.init
  (:require [pyjama.agent.hooks.dashboard :as dashboard]))

(defn init! []
  ;; Start dashboard automatically
  (dashboard/start-dashboard! 8080)
  
  ;; ... other initialization
  )
```

## 📡 API Endpoints

### GET /
Returns the HTML dashboard page

### GET /api/data
Returns JSON with current state:

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
      "success-rate": 0.974,
      "avg-duration-ms": 45.23,
      "throughput": 2.34
    },
    "tools": {...},
    "agents": {...}
  },
  "recent-logs": [
    {
      "timestamp": 1706945873123,
      "agent-id": "software-versions-v2",
      "tool": "write-file",
      "status": "ok"
    }
  ],
  "hooks": {
    "registered": {
      "write-file": 3,
      "read-files": 2
    }
  }
}
```

## 🎯 Use Cases

### 1. **Development Monitoring**
```clojure
;; Start dashboard
(dashboard/start-dashboard!)

;; Run your agents
;; Watch them in real-time!
```

### 2. **Production Monitoring**
```clojure
;; Start dashboard on custom port
(dashboard/start-dashboard! 9090)

;; Monitor production agents
;; Track performance metrics
```

### 3. **Debugging**
```clojure
;; Start dashboard
(dashboard/start-dashboard!)

;; Run problematic agent
;; Watch logs in real-time
;; See exactly where it fails
```

### 4. **Performance Analysis**
```clojure
;; Start dashboard
(dashboard/start-dashboard!)

;; Run multiple agents
;; Compare execution times
;; Identify bottlenecks
```

## 🔥 Example Session

```clojure
;; 1. Start dashboard
(require '[pyjama.agent.hooks.dashboard :as dashboard])
(dashboard/start-dashboard! 8080)
;; => 🚀 Dashboard server started on http://localhost:8080

;; 2. Open browser to http://localhost:8080

;; 3. Run some agents (in another terminal)
;; $ clj -M:pyjama run software-versions-v2 '{"project-dir":"."}'
;; $ clj -M:pyjama run architecture-diagram-agent '{"project-dir":"."}'

;; 4. Watch the dashboard update in real-time!
;;    - Agents appear in "Active Agents"
;;    - Metrics update every 2 seconds
;;    - Logs stream in "Recent Activity"
;;    - Hook counts show activity

;; 5. When done
(dashboard/stop-dashboard!)
;; => 🛑 Dashboard server stopped
```

## 🎨 Screenshot Description

The dashboard shows:

```
╔════════════════════════════════════════════════════════════════╗
║              🎣 Pyjama Agent Dashboard                         ║
║     Real-time monitoring of agent execution, metrics, hooks    ║
╚════════════════════════════════════════════════════════════════╝

┌─────────────────────┬─────────────────────┬─────────────────────┐
│  📊 Global Metrics  │  🤖 Active Agents   │  🔧 Registered Hooks│
│                     │                     │                     │
│  Total: 156         │  software-versions  │  write-file: 3      │
│  Success: 97.4%     │  [RUNNING] 2.3s     │  read-files: 2      │
│  Avg: 45.23ms       │                     │  list-directory: 2  │
│  Throughput: 2.34   │  arch-diagram       │  cat-files: 2       │
│                     │  [COMPLETED] 5.1s   │  discover: 2        │
└─────────────────────┴─────────────────────┴─────────────────────┘

┌───────────────────────────────────────────────────────────────┐
│  📝 Recent Activity                                           │
│                                                               │
│  16:35:57 software-versions executed write-file - ok         │
│  16:35:56 software-versions executed read-files - ok         │
│  16:35:55 software-versions executed discover-codebase - ok  │
│  16:35:54 arch-diagram executed write-file - ok              │
│  ...                                                          │
└───────────────────────────────────────────────────────────────┘

              ● Auto-refreshing every 2 seconds
```

## 🚀 Advanced Usage

### Custom Tracking

```clojure
;; Add custom tracking to your hooks
(require '[pyjama.agent.hooks :as hooks]
         '[pyjama.agent.hooks.dashboard :as dashboard])

(hooks/register-hook! :my-custom-tool
  (fn [{:keys [ctx result]}]
    ;; Your logic here
    ;; Dashboard will automatically track it!
    ))
```

### Programmatic Access

```clojure
;; Get current dashboard state
@dashboard/dashboard-state

;; Get dashboard data as JSON
(dashboard/get-dashboard-data)
```

## 📚 Integration Examples

### With Codebase Analyzer

```clojure
(ns codebase-analyzer.init
  (:require [pyjama.agent.hooks.dashboard :as dashboard]))

(defn init! []
  ;; Start dashboard
  (dashboard/start-dashboard! 8080)
  
  ;; ... other initialization
  )
```

### With Custom Agents

```clojure
(ns my-agent.core
  (:require [pyjama.agent.hooks.dashboard :as dashboard]))

(defn -main [& args]
  ;; Start dashboard
  (dashboard/start-dashboard!)
  
  ;; Run your agent
  (run-my-agent)
  
  ;; Dashboard shows everything in real-time!
  )
```

## 🎉 Benefits

1. **Real-Time Visibility** - See exactly what's happening
2. **Beautiful UI** - Professional, modern interface
3. **Zero Config** - Works out of the box
4. **Auto-Refresh** - Always up-to-date
5. **Multi-Agent** - Track multiple agents simultaneously
6. **Performance Insights** - Identify bottlenecks instantly
7. **Debugging Aid** - See logs as they happen
8. **Production Ready** - Monitor production agents

## 🔮 Future Enhancements

Potential additions:
- 📈 Historical graphs
- 🔍 Log filtering and search
- 📊 Custom metrics
- 🔔 Alert configuration
- 💾 Export data
- 🎨 Custom themes
- 📱 Mobile optimization
- 🔐 Authentication

## 🎊 Summary

The Pyjama Agent Dashboard provides:

✅ **Real-time monitoring** - Live updates every 2 seconds  
✅ **Beautiful UI** - Modern, gradient purple theme  
✅ **Active agents** - See what's running right now  
✅ **Live metrics** - Performance stats in real-time  
✅ **Log streaming** - Recent activity feed  
✅ **Hook status** - See registered hooks  
✅ **Zero config** - Just start and go  
✅ **Multi-agent** - Track multiple agents  

**One command to see everything!** 🚀

```clojure
(dashboard/start-dashboard!)
```

**Open http://localhost:8080 and watch the magic!** ✨
