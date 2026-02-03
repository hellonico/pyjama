# 🎣 Pyjama Hooks Ecosystem - Complete Guide

## 🎉 Overview

The Pyjama Hooks System provides a powerful, extensible framework for adding cross-cutting concerns to agent tool execution. With support for **logging**, **metrics**, **notifications**, **validation**, and **custom hooks**, you can monitor, track, and enhance agent behavior without modifying agent definitions.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     HOOKS LIFECYCLE                         │
│                                                             │
│  1. PRE-EXECUTION HOOKS                                     │
│     ├─ Validation                                           │
│     ├─ Argument modification                                │
│     └─ Resource setup                                       │
│                                                             │
│  2. TOOL EXECUTION                                          │
│     └─ Tool runs normally                                   │
│                                                             │
│  3. POST-EXECUTION HOOKS                                    │
│     ├─ Logging                                              │
│     ├─ Metrics tracking                                     │
│     ├─ Notifications                                        │
│     ├─ Auto-indexing                                        │
│     └─ Custom processing                                    │
└─────────────────────────────────────────────────────────────┘
```

## 📦 Available Hook Modules

### 1. **Core Hooks** (`pyjama.agent.hooks`)
Base hook system with pre/post execution support.

### 2. **Logging Hooks** (`pyjama.agent.hooks.logging`)
Automatic logging of tool executions with configurable formats.

### 3. **Metrics Hooks** (`pyjama.agent.hooks.metrics`)
Performance tracking and statistical analysis.

### 4. **Notification Hooks** (`pyjama.agent.hooks.notifications`)
Alerts and notifications for errors, completions, and custom events.

### 5. **Hooks Manager** (`pyjama.agent.hooks.manager`)
Centralized management and easy setup.

## 🚀 Quick Start

### Option 1: Enable Everything (Recommended)

```clojure
(require '[pyjama.agent.hooks.manager :as manager])

;; Enable all hooks with defaults
(manager/enable-all-hooks!)

;; Run your agents...

;; View metrics
(manager/show-metrics)

;; Check status
(manager/print-hooks-status)
```

### Option 2: Custom Configuration

```clojure
(require '[pyjama.agent.hooks.manager :as manager])

(manager/enable-all-hooks!
  :logging true
  :metrics true
  :notifications true
  :log-level :debug
  :log-output :file
  :log-file "/var/log/pyjama/tools.log"
  :log-format :json
  :notify-errors true
  :notify-completion true)
```

### Option 3: Manual Setup (Advanced)

```clojure
;; Logging
(require '[pyjama.agent.hooks.logging :as log])
(log/configure! {:level :info :format :pretty})
(log/register-logging-hooks!)

;; Metrics
(require '[pyjama.agent.hooks.metrics :as metrics])
(metrics/register-metrics-hooks!)

;; Notifications
(require '[pyjama.agent.hooks.notifications :as notif])
(notif/register-handler! :console notif/console-handler)
(notif/register-notification-hooks! :on-error true)
```

## 📊 Logging Hooks

### Features
- **Multiple formats**: Pretty, JSON, EDN
- **Multiple outputs**: stdout, stderr, file
- **Configurable verbosity**: Include/exclude args and results
- **Truncation**: Prevent log spam with max-length limits

### Configuration

```clojure
(require '[pyjama.agent.hooks.logging :as log])

(log/configure!
  {:enabled true
   :level :info              ;; :debug, :info, :warn, :error
   :output :file             ;; :stdout, :stderr, :file
   :file-path "/tmp/pyjama.log"
   :format :json             ;; :pretty, :json, :edn
   :include-args true
   :include-result true
   :max-length 200})

(log/register-logging-hooks!)
```

### Example Output

**Pretty format:**
```
[2026-02-03 16:17:55.123] INFO Agent:software-versions-v2 Tool:write-file Status:ok
```

**JSON format:**
```json
{"timestamp":"2026-02-03 16:17:55.123","level":"info","agent-id":"software-versions-v2","tool":"write-file","status":"ok","args":{...},"result":{...}}
```

## 📈 Metrics Hooks

### Features
- **Execution counts**: Total, success, error
- **Duration tracking**: Min, max, avg, median, p95, p99
- **Success rates**: Per-tool and per-agent
- **Throughput**: Operations per second
- **Uptime tracking**: System uptime

### Usage

```clojure
(require '[pyjama.agent.hooks.metrics :as metrics])

;; Register metrics tracking
(metrics/register-metrics-hooks!)

;; Run agents...

;; View comprehensive summary
(metrics/print-metrics-summary)

;; Get specific metrics
(metrics/get-tool-metrics :write-file)
;; => {:count 42 :success 40 :error 2 :success-rate 0.95
;;     :min 10.5 :max 250.3 :avg 45.2 :median 42.1 :p95 120.5 :p99 200.8}

(metrics/get-agent-metrics "software-versions-v2")
(metrics/get-global-metrics)

;; Reset metrics
(metrics/reset-metrics!)
```

### Example Output

```
╔════════════════════════════════════════════════════════════════╗
║                    PYJAMA METRICS SUMMARY                      ║
╚════════════════════════════════════════════════════════════════╝

📊 Global Metrics:
   Total Executions: 156
   Success: 152 (97.4%)
   Errors: 4
   Avg Duration: 45.23ms
   Throughput: 2.34 ops/sec
   Uptime: 66.67s

🔧 Tool Metrics:
   :write-file:
     Executions: 42
     Success Rate: 95.2%
     Duration: avg=45.20ms median=42.10ms p95=120.50ms
   
   :read-files:
     Executions: 38
     Success Rate: 100.0%
     Duration: avg=12.50ms median=11.20ms p95=25.30ms

🤖 Agent Metrics:
   software-versions-v2:
     Executions: 78
     Success Rate: 96.2%
     Avg Duration: 38.45ms
```

## 🔔 Notification Hooks

### Features
- **Pluggable handlers**: Console, file, webhook, custom
- **Event types**: Errors, completions, file writes, custom
- **Multiple handlers**: Send to multiple destinations simultaneously

### Built-in Handlers

```clojure
(require '[pyjama.agent.hooks.notifications :as notif])

;; Console handler (default)
(notif/register-handler! :console notif/console-handler)

;; File handler
(notif/register-handler! :file 
  (notif/file-handler "/var/log/pyjama/notifications.log"))

;; Webhook handler (Slack, Discord, etc.)
(notif/register-handler! :slack 
  (notif/webhook-handler "https://hooks.slack.com/..."))
```

### Custom Handler

```clojure
(notif/register-handler! :email
  (fn [{:keys [title message level data]}]
    (send-email {:to "admin@example.com"
                 :subject title
                 :body message
                 :priority (if (= level :error) "high" "normal")})))
```

### Enable Notifications

```clojure
(notif/register-notification-hooks!
  :on-error true          ;; Notify on errors
  :on-completion false    ;; Notify on successful completion
  :on-file-write true)    ;; Notify when files are written
```

## 🎯 Pre-Execution Hooks

Pre-hooks run **before** tool execution and can:
- ✅ Validate inputs
- ✅ Modify arguments
- ✅ Set up resources
- ✅ Cancel execution (by throwing)

### Example: Input Validation

```clojure
(require '[pyjama.agent.hooks :as hooks])

(hooks/register-pre-hook! :write-file
  (fn [{:keys [args]}]
    (when-not (:path args)
      (throw (ex-info "Missing required :path argument" 
                      {:args args})))
    (when (empty? (:message args))
      (throw (ex-info "Cannot write empty file" 
                      {:args args})))))
```

### Example: Argument Modification

```clojure
(hooks/register-pre-hook! :write-file
  (fn [hook-data]
    ;; Add header to all written files
    (update-in hook-data [:args :message]
               #(str "<!-- Generated by Pyjama -->\n" %))))
```

### Example: Resource Setup

```clojure
(hooks/register-pre-hook! :write-file
  (fn [{:keys [args ctx]}]
    ;; Ensure output directory exists
    (let [dir (-> args :path io/file .getParent)]
      (when dir
        (.mkdirs (io/file dir))))))
```

## 🔧 Custom Hooks

Create your own hooks for any purpose:

```clojure
(require '[pyjama.agent.hooks :as hooks])

;; Custom post-execution hook
(defn my-custom-hook
  [{:keys [tool-name args result ctx params]}]
  (println "Custom processing for" tool-name)
  ;; Your logic here
  )

;; Register it
(hooks/register-hook! :write-file my-custom-hook)

;; Custom pre-execution hook
(defn my-validation-hook
  [{:keys [tool-name args ctx params]}]
  (println "Validating" tool-name)
  ;; Your validation logic
  ;; Return modified hook-data or nil
  )

(hooks/register-pre-hook! :write-file my-validation-hook)
```

## 📋 Hooks Manager

Centralized management for all hooks:

```clojure
(require '[pyjama.agent.hooks.manager :as manager])

;; Enable all hooks
(manager/enable-all-hooks!)

;; Check what's registered
(manager/print-hooks-status)

;; View metrics
(manager/show-metrics)

;; Disable everything
(manager/disable-all-hooks!)
```

## 🎨 Complete Example

```clojure
(ns my-project.init
  (:require [pyjama.agent.hooks.manager :as manager]
            [pyjama.agent.hooks.logging :as log]
            [pyjama.agent.hooks.metrics :as metrics]
            [pyjama.agent.hooks.notifications :as notif]))

(defn init!
  "Initialize hooks system"
  []
  (println "🚀 Initializing hooks...")
  
  ;; Enable all hooks with custom config
  (manager/enable-all-hooks!
    :logging true
    :metrics true
    :notifications true
    :log-level :info
    :log-output :file
    :log-file "/var/log/pyjama/tools.log"
    :log-format :json
    :notify-errors true
    :notify-handlers [:console :file])
  
  ;; Add custom notification handler
  (notif/register-handler! :file
    (notif/file-handler "/var/log/pyjama/notifications.log"))
  
  (println "✅ Hooks initialized"))

(defn shutdown!
  "Clean shutdown"
  []
  (println "📊 Final metrics:")
  (metrics/print-metrics-summary)
  (manager/disable-all-hooks!))
```

## 🔍 Debugging

### Check Hook Status

```clojure
(require '[pyjama.agent.hooks :as hooks])

;; See what hooks are registered
(hooks/get-hooks :write-file)
(hooks/get-pre-hooks :write-file)

;; Check all tools
(require '[pyjama.agent.hooks.manager :as manager])
(manager/print-hooks-status)
```

### Test Hooks Manually

```clojure
;; Test a post-execution hook
(hooks/run-hooks! :write-file
  {:tool-name :write-file
   :args {:path "/tmp/test.md" :message "test"}
   :result {:status :ok :file "/tmp/test.md"}
   :ctx {:id "test-agent"}
   :params {}})

;; Test a pre-execution hook
(hooks/run-pre-hooks! :write-file
  {:tool-name :write-file
   :args {:path "/tmp/test.md" :message "test"}
   :ctx {:id "test-agent"}
   :params {}})
```

## 🎯 Best Practices

1. **Use the Manager**: Start with `manager/enable-all-hooks!` for simplicity
2. **Configure Logging**: Use JSON format for production, pretty for development
3. **Monitor Metrics**: Regularly check metrics to identify bottlenecks
4. **Validate Early**: Use pre-hooks for input validation
5. **Fail Gracefully**: Hooks should never crash the agent
6. **Keep Hooks Fast**: Hooks run synchronously, keep them lightweight
7. **Use Appropriate Levels**: Debug for development, info for production

## 📚 API Reference

### Core Hooks (`pyjama.agent.hooks`)
- `register-hook!` - Register post-execution hook
- `register-pre-hook!` - Register pre-execution hook
- `unregister-hook!` - Remove post-execution hook
- `unregister-pre-hook!` - Remove pre-execution hook
- `get-hooks` - Get all post-execution hooks
- `get-pre-hooks` - Get all pre-execution hooks
- `run-hooks!` - Execute post-execution hooks
- `run-pre-hooks!` - Execute pre-execution hooks
- `clear-hooks!` - Clear all hooks

### Logging (`pyjama.agent.hooks.logging`)
- `configure!` - Configure logging behavior
- `register-logging-hooks!` - Enable logging
- `unregister-logging-hooks!` - Disable logging

### Metrics (`pyjama.agent.hooks.metrics`)
- `register-metrics-hooks!` - Enable metrics tracking
- `unregister-metrics-hooks!` - Disable metrics tracking
- `get-tool-metrics` - Get metrics for a tool
- `get-agent-metrics` - Get metrics for an agent
- `get-global-metrics` - Get global metrics
- `get-all-metrics` - Get comprehensive report
- `print-metrics-summary` - Print human-readable summary
- `reset-metrics!` - Reset all metrics

### Notifications (`pyjama.agent.hooks.notifications`)
- `register-handler!` - Register notification handler
- `unregister-handler!` - Remove notification handler
- `register-notification-hooks!` - Enable notifications
- `unregister-notification-hooks!` - Disable notifications
- `console-handler` - Built-in console handler
- `file-handler` - Built-in file handler
- `webhook-handler` - Built-in webhook handler

### Manager (`pyjama.agent.hooks.manager`)
- `enable-all-hooks!` - Enable all hooks with config
- `disable-all-hooks!` - Disable all hooks
- `print-hooks-status` - Show current status
- `show-metrics` - Display metrics summary

## 🎉 Conclusion

The Pyjama Hooks System provides a powerful, flexible foundation for monitoring, tracking, and enhancing agent behavior. With logging, metrics, notifications, and custom hooks, you have everything you need to build production-ready agent systems!

Happy hooking! 🎣
