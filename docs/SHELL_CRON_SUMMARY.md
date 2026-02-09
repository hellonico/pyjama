# Summary: Shell and Cron Tools for Pyjama

## Overview
Added two new agent tools to the Pyjama framework:

### 1. Shell Tool (`pyjama.tools.shell`)
Execute shell commands and scripts from within agents.

**Features:**
- Execute commands as strings or vectors
- Set working directory
- Environment variables support
- Configurable timeouts (default 60s)
- Run multi-line shell scripts
- Custom shell interpreter support
- **Automatic environment variable expansion** ($HOME, ${USER})
- **Glob pattern expansion** (*.clj, src/**/*.java)

**Functions:**
- `execute-command` - Run a single command
- `run-script` - Execute a shell script

### 2. Cron Tool (`pyjama.tools.cron`)
Schedule recurring and one-time tasks using cron-like expressions.

**Features:**
- Simplified cron syntax (`@hourly`, `@daily`, `*/N * * * *`, etc.)
- Recurring scheduled tasks
- One-time delayed execution
- Task management (list, cancel)
- Thread-safe scheduling with fixed thread pool

**Functions:**
- `schedule-task` - Schedule recurring tasks
- `run-once-after` - One-time delayed execution
- `list-tasks` - View all scheduled tasks
- `cancel-task` - Stop a scheduled task

## Files Created

### Core Implementation
- `/src/pyjama/tools/shell.clj` - Shell command execution
- `/src/pyjama/tools/cron.clj` - Task scheduling

### Tests
- `/test/pyjama/tools/shell_test.clj` - Shell tool tests (12 assertions)
- `/test/pyjama/tools/cron_test.clj` - Cron tool tests (13 assertions)

### Documentation
- `/docs/SHELL_AND_CRON_TOOLS.md` - Comprehensive usage guide

### Examples
- `/examples/system-monitor-example.edn` - Example agent using both tools

### Updates
- Updated `/README.md` to reference new tools and example

## Test Results
All tests passing:
- ✅ Shell tool: 2 tests, 12 assertions
- ✅ Cron tool: 3 tests, 13 assertions

## Usage Examples

### Shell
```clojure
(require '[pyjama.tools.shell :as shell])

;; Simple command
(shell/execute-command {:command "ls -la"})

;; With options
(shell/execute-command 
  {:command "git status"
   :dir "/path/to/repo"
   :timeout 5000})

;; Environment variable expansion (automatic)
(shell/execute-command {:command "echo $HOME"})
;; => Expands to actual home directory

;; Glob pattern expansion (automatic)
(shell/execute-command {:command ["ls" "*.clj"]})
;; => Expands to all .clj files in current directory

;; Combine both
(shell/execute-command {:command "cat $HOME/docs/*.md"})
;; => Expands $HOME and *.md pattern

;; Disable expansions if needed
(shell/execute-command 
  {:command "echo '*.txt'"
   :expand-env? false
   :expand-glob? false})

;; Run script
(shell/run-script 
  {:script "echo 'Starting...'\ndate\necho 'Done!'"})
```

### Cron
```clojure
(require '[pyjama.tools.cron :as cron])

;; Schedule recurring task
(cron/schedule-task
  {:id "backup"
   :schedule "*/10 * * * *"  ; Every 10 minutes
   :task #(do-backup!)
   :description "Regular backup"})

;; One-time delayed task
(cron/run-once-after
  {:id "cleanup"
   :delay 300  ; 5 minutes
   :task #(cleanup-temp-files!)})

;; Manage tasks
(cron/list-tasks)
(cron/cancel-task {:id "backup"})
```

### Combined Example
```clojure
;; Schedule a shell command to run every hour
(cron/schedule-task
  {:id "git-pull"
   :schedule "@hourly"
   :task #(shell/execute-command 
            {:command "git pull"
             :dir "/repo"})
   :description "Auto-sync repository"})
```

## Next Steps
These tools are ready for use in Pyjama agents. They can be:
- Referenced in agent EDN files
- Combined with other tools (LLM, file I/O, etc.)
- Used to build automation and monitoring agents
- Extended with additional features as needed

## Design Decisions

1. **Shell Tool**: Used `clojure.java.shell` for cross-platform compatibility
2. **Cron Tool**: Simplified cron syntax (doesn't support full cron spec)
3. **Timeouts**: Default 60s timeout prevents hanging on stuck commands
4. **Error Handling**: Consistent `:status :ok/:error` return format
5. **Thread Pool**: Fixed 4-thread pool for scheduled tasks
6. **In-Memory**: Tasks don't persist across JVM restarts (intentional for MVP)
