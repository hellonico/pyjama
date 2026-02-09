# Shell and Cron Tools for Pyjama Agents

## Overview

This document describes two new agent tools:
- **shell**: Execute shell commands and scripts
- **cron**: Schedule recurring tasks using cron-like expressions

## Shell Tool

### Basic Command Execution

Execute simple shell commands:

```clojure
(require '[pyjama.tools.shell :as shell])

;; Simple string command
(shell/execute-command {:command "ls -la"})
;; => {:status :ok 
;;     :exit 0 
;;     :out "..." 
;;     :err "" 
;;     :command "ls -la"}

;; Vector format (safer for arguments with spaces)
(shell/execute-command {:command ["git" "log" "--oneline" "-n" "5"]})
```

### Working Directory

Specify a working directory:

```clojure
(shell/execute-command 
  {:command "pwd"
   :dir "/tmp"})
;; => {:status :ok :out "/tmp\n" ...}
```

### Environment Variable Expansion

Environment variables are automatically expanded by default:

```clojure
;; $VAR and ${VAR} syntax both work
(shell/execute-command {:command "echo $HOME"})
;; => {:status :ok :out "/Users/username\n" ...}

(shell/execute-command {:command "echo ${USER}_backup"})
;; => {:status :ok :out "username_backup\n" ...}

;; Disable env var expansion
(shell/execute-command 
  {:command "echo '$HOME'"
   :expand-env? false})
;; => {:status :ok :out "$HOME\n" ...}
```

### Glob Pattern Expansion

Glob patterns like `*`, `?`, and `[...]` are automatically expanded:

```clojure
;; Expand *.clj to all Clojure files
(shell/execute-command {:command ["ls" "src/*.clj"]})
;; => Lists all .clj files in src/

;; Disable glob expansion
(shell/execute-command 
  {:command ["echo" "*.txt"]
   :expand-glob? false})
;; => {:status :ok :out "*.txt\n" ...}

;; Combine with env vars
(shell/execute-command {:command "ls $HOME/*.pdf"})
;; => Lists all PDFs in home directory
```

**Supported glob patterns:**
- `*` - matches any characters
- `?` - matches a single character
- `[abc]` - matches any character in the set
- `**` - matches across directories (shell-dependent)

### Environment Variables

Pass custom environment variables:

```clojure
(shell/execute-command 
  {:command "echo $MY_VAR"
   :env {"MY_VAR" "hello"}})
```

### Timeout Control

Set a timeout (default is 60000ms = 1 minute):

```clojure
(shell/execute-command 
  {:command "sleep 10"
   :timeout 5000}) ; Will timeout after 5 seconds
;; => {:status :error 
;;     :message "Command timed out after 5000ms" ...}
```

### Script Execution

Execute multi-line shell scripts:

```clojure
(shell/run-script 
  {:script "#!/bin/bash
            echo 'Starting backup...'
            tar -czf backup.tar.gz /data
            echo 'Backup complete!'"})

;; Or from a file
(shell/run-script {:file "/path/to/script.sh"})

;; Specify shell interpreter
(shell/run-script 
  {:script "print('Hello from Python')"
   :shell "/usr/bin/python3"})
```

## Cron Tool

### Schedule Recurring Tasks

Schedule tasks using cron-like expressions:

```clojure
(require '[pyjama.tools.cron :as cron])

;; Every 5 minutes
(cron/schedule-task
  {:id "backup-job"
   :schedule "*/5 * * * *"
   :task #(println "Running backup...")
   :description "Automated backup every 5 minutes"})

;; Hourly
(cron/schedule-task
  {:id "hourly-sync"
   :schedule "@hourly"
   :task #(sync-data!)
   :description "Sync data every hour"})

;; Daily at midnight
(cron/schedule-task
  {:id "daily-report"
   :schedule "@daily"
   :task #(generate-report!)
   :description "Generate daily report"})

;; Daily at specific time (9:30 AM)
(cron/schedule-task
  {:id "morning-task"
   :schedule "30 9 * * *"
   :task #(send-morning-email!)
   :description "Send morning email at 9:30 AM"})
```

### Supported Cron Expressions

| Expression | Description |
|------------|-------------|
| `@hourly` | Every hour |
| `@daily` | Every day at midnight |
| `@weekly` | Every week on Sunday at midnight |
| `*/N * * * *` | Every N minutes |
| `0 */N * * *` | Every N hours |
| `M H * * *` | Daily at specific time (M=minute, H=hour) |
| `0 0 * * *` | Daily at midnight |

### One-Time Delayed Tasks

Schedule a task to run once after a delay:

```clojure
(cron/run-once-after
  {:id "delayed-task"
   :delay 300 ; seconds
   :task #(cleanup-temp-files!)
   :description "Cleanup temp files after 5 minutes"})
```

### Task Management

List all scheduled tasks:

```clojure
(cron/list-tasks)
;; => {:status :ok
;;     :count 3
;;     :tasks [{:id "backup-job"
;;              :schedule "*/5 * * * *"
;;              :description "..."
;;              :created-at 1234567890
;;              :cancelled? false}
;;             ...]}
```

Cancel a scheduled task:

```clojure
(cron/cancel-task {:id "backup-job"})
;; => {:status :ok :id "backup-job"}
```

## Agent Integration Examples

### Example 1: Scheduled Shell Commands

Combine both tools to schedule recurring shell commands:

```clojure
(cron/schedule-task
  {:id "git-pull"
   :schedule "*/15 * * * *" ; Every 15 minutes
   :task #(shell/execute-command 
            {:command "git pull"
             :dir "/path/to/repo"})
   :description "Auto-pull git repository"})
```

### Example 2: System Monitoring Agent

```clojure
(defn check-disk-space []
  (let [result (shell/execute-command {:command "df -h"})]
    (when (= :ok (:status result))
      (println "Disk usage:" (:out result)))))

(cron/schedule-task
  {:id "disk-monitor"
   :schedule "0 */2 * * *" ; Every 2 hours
   :task check-disk-space
   :description "Monitor disk space"})
```

### Example 3: Backup Agent

```clojure
(defn create-backup []
  (let [timestamp (.format 
                   (java.time.LocalDateTime/now)
                   (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss"))
        backup-dir (str "/backups/backup-" timestamp)]
    (shell/run-script
      {:script (str "#!/bin/bash\n"
                    "mkdir -p " backup-dir "\n"
                    "tar -czf " backup-dir "/data.tar.gz /data\n"
                    "echo 'Backup created at " backup-dir "'\n")})))

;; Daily backup at 2 AM
(cron/schedule-task
  {:id "daily-backup"
   :schedule "0 2 * * *"
   :task create-backup
   :description "Daily backup at 2 AM"})
```

### Example 4: Agent EDN Configuration

Use these tools in an agent specification:

```edn
{:name "system-monitor"
 :description "Monitor system health and send alerts"
 :tools {:execute-command pyjama.tools.shell/execute-command
         :run-script pyjama.tools.shell/run-script
         :schedule-task pyjama.tools.cron/schedule-task
         :cancel-task pyjama.tools.cron/cancel-task
         :list-tasks pyjama.tools.cron/list-tasks}
 :loop {:type :template
        :template "monitor-template"
        :params {:check-interval-minutes 5}}}
```

## Error Handling

Both tools return standardized status maps:

```clojure
;; Success
{:status :ok
 :exit 0
 :out "output"
 :err ""
 ...}

;; Error
{:status :error
 :message "error description"
 :exit 1  ; for shell commands
 ...}
```

Always check the `:status` field to handle errors appropriately:

```clojure
(let [result (shell/execute-command {:command "risky-command"})]
  (if (= :ok (:status result))
    (println "Success:" (:out result))
    (println "Error:" (:message result))))
```

## Best Practices

1. **Always specify timeouts** for long-running commands
2. **Use vector format** for commands with complex arguments
3. **Check exit codes** and handle errors gracefully
4. **Use descriptive IDs** for scheduled tasks
5. **Clean up tasks** when no longer needed (use `cancel-task`)
6. **Test scripts** before scheduling them
7. **Monitor task execution** using logging within task functions
8. **Consider timezone** - cron times are in system timezone

## Limitations

1. **Simplified Cron Syntax**: Not all cron features are supported (e.g., month/day-of-week ranges)
2. **Task Persistence**: Tasks are in-memory only and won't survive JVM restarts
3. **Maximum Concurrency**: The scheduler uses a fixed thread pool (4 threads)
4. **No Output Capture for Scheduled Tasks**: Stdout from scheduled tasks goes to console

## Future Enhancements

Potential improvements for future versions:
- Full cron syntax support
- Task persistence to disk/database
- Configurable thread pool size
- Output capture and logging for scheduled tasks
- Web UI for task management
- Task dependency chains
- Retry logic with exponential backoff

## Integration Examples

For complete working examples demonstrating the shell and cron tools together:

- **[CRON_SHELL_INTEGRATION.md](CRON_SHELL_INTEGRATION.md)** - Quick reference and test summary
- **[CRON_SHELL_DEMO.md](CRON_SHELL_DEMO.md)** - Detailed integration guide with examples
- **[examples/date-printer-agent.edn](../examples/date-printer-agent.edn)** - Pure EDN agent using loops and shell commands
- **[test/pyjama/tools/run_cron_shell_demo.clj](../test/pyjama/tools/run_cron_shell_demo.clj)** - Standalone demo script
