# Cron + Shell Integration Demo

This directory contains examples demonstrating how to use the **cron** and **shell** tools together in Pyjama agents.

## Quick Demo

The simplest way to see both tools in action:

```bash
clojure -M test/pyjama/tools/run_cron_shell_demo.clj
```

**Output:**
```
╔════════════════════════════════════════════════╗
║  Cron + Shell Integration Test               ║
║  Printing date every second for 5 seconds    ║
╚════════════════════════════════════════════════╝

⏱  Tasks scheduled. Waiting for execution...

│ [Task 0] Mon Feb  9 09:02:41 JST 2026
│ [Task 1] Mon Feb  9 09:02:42 JST 2026
│ [Task 2] Mon Feb  9 09:02:43 JST 2026
│ [Task 3] Mon Feb  9 09:02:44 JST 2026
│ [Task 4] Mon Feb  9 09:02:45 JST 2026

✓ All tasks completed!

ℹ  Remaining scheduled tasks: 0
```

## What It Does

The demo:
1. **Schedules 5 tasks** using `cron/run-once-after`
2. Each task runs the shell `date` command
3. Tasks execute at 1-second intervals (1s, 2s, 3s, 4s, 5s)
4. Prints the output from each execution
5. Auto-cleanup after completion (one-time tasks)

## Files

### Test Files

- **`test/pyjama/tools/cron_shell_integration_test.clj`**  
  Full integration test suite with assertions

- **`test/pyjama/tools/run_cron_shell_demo.clj`**  
  Simple standalone demo script (easiest to run)

### Example Files

- **`examples/cron_shell_demo.clj`**  
  Programmatic example with `-main` function

- **`examples/date-printer-agent.edn`**  
  Pure EDN agent specification (no code required)

## Running the Examples

### 1. Standalone Script (Recommended)

```bash
cd /Users/nico/cool/origami-nightweave/pyjama
clojure -M test/pyjama/tools/run_cron_shell_demo.clj
```

### 2. Integration Test

```bash
clojure -X:test :patterns '["pyjama.tools.cron-shell-integration-test"]'
```

### 3. Programmatic Example

```bash
clojure -M -m cron-shell-demo
```

### 4. As a REPL Demo

```clojure
(load-file "test/pyjama/tools/run_cron_shell_demo.clj")
```

## Key Concepts Demonstrated

### Cron Tool Features
- ✅ `run-once-after` - Schedule one-time delayed tasks
- ✅ Task IDs and descriptions
- ✅ Delay in seconds
- ✅ Auto-cleanup of one-time tasks
- ✅ `list-tasks` for monitoring

### Shell Tool Features
- ✅ `execute-command` - Run shell commands
- ✅ Return status and output
- ✅ Command output parsing

### Integration
- ✅ Combining both tools in task functions
- ✅ Scheduling shell command execution
- ✅ Automated workflows with timed shell operations

## Code Snippet

The core pattern used in all examples:

```clojure
(require '[pyjama.tools.cron :as cron])
(require '[pyjama.tools.shell :as shell])

;; Schedule tasks at 1-second intervals
(doseq [i (range 5)]
  (cron/run-once-after
   {:id (str "task-" i)
    :delay (inc i)  ; 1, 2, 3, 4, 5 seconds
    :task (fn []
            (let [result (shell/execute-command {:command "date"})]
              (println (str "[Task " i "] " (:out result)))))
    :description (str "Task " i)}))

;; Wait for completion
(Thread/sleep 6000)
```

## Customization

You can easily modify the demo to:

- **Change the interval**: Modify the `:delay` calculation
- **Run different commands**: Replace `"date"` with any shell command
- **Add more tasks**: Increase the range in `(range 5)`
- **Use recurring schedules**: Use `schedule-task` instead for cron-style scheduling

## Use Cases

This pattern is useful for:

1. **Scheduled backups** - Run backup scripts at intervals
2. **Health checks** - Monitor system status periodically
3. **Data collection** - Gather metrics on a schedule
4. **Automated maintenance** - Run cleanup tasks
5. **Notification systems** - Send alerts at specific times

## Next Steps

See the full documentation:
- [Shell and Cron Tools Guide](../docs/SHELL_AND_CRON_TOOLS.md)
- [Shell Tool Examples](../examples/shell_tool_examples.clj)
- [System Monitor Example](../examples/system-monitor-example.edn)
