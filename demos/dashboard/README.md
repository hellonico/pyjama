# Pyjama Dashboard Demo

This demo showcases the Pyjama agent dashboard by launching multiple small agents in parallel.

## Quick Start

1. **Start the dashboard** (if not already running):
   ```bash
   cd /Users/nico/cool/origami-nightweave/pyjama
   ./start-dashboard.sh
   ```

2. **Launch the demo agents**:
   ```bash
   cd demos/dashboard
   ./launch-demo.sh
   ```

3. **Open the dashboard** in your browser:
   ```
   http://localhost:8090
   ```

## What You'll See

The demo launches 5 concurrent agents:

### 1. 🌍 **hello-world**
- Writes a greeting message
- Confirms file creation
- **Steps**: 2

### 2. 📁 **file-counter**
- Lists files in directory
- Counts and reports results
- **Steps**: 3

### 3. ✍️ **poem-writer**
- Composes a poem about coding
- Reviews and enhances it
- **Steps**: 3

### 4. 🔢 **quick-math**
- Performs simple calculations
- Verifies results
- **Steps**: 2

### 5. 💻 **code-snippet**
- Writes a Python function
- Documents it
- Reviews both files
- **Steps**: 3

## Dashboard Features to Explore

### 📊 Metrics Tab
- Total executions across all agents
- Success rate
- Average duration
- **Throughput** (operations per second)

### 🤖 Active Agents Tab
- See all 5 agents running simultaneously
- Click any agent card to see its full workflow
- Watch steps complete in real-time
- See duration for each agent

### 📝 Activity Tab
- Real-time log of all tool executions
- See which agent is doing what
- Monitor write-file, read-files operations
- Condensed single-line format

## Output

All agent outputs are saved to:
```
demos/dashboard/output/
```

Logs for each agent:
```
demos/dashboard/output/*.log
```

## Customization

### Add Your Own Agent

1. Create a new EDN file in `agents/`:
   ```clojure
   {:name "my-agent"
    :description "My custom agent"
    :steps
    [{:id "step1"
      :prompt "Do something interesting"
      :tools [:write-file]}]}
   ```

2. Add it to `launch-demo.sh`:
   ```bash
   clj -M:pyjama run demos/dashboard/agents/my-agent.edn "{}" > "$OUTPUT_DIR/my-agent.log" 2>&1 &
   ```

### Adjust Timing

Change the `sleep` values in `launch-demo.sh` to:
- **Faster**: Reduce to `0.2` for rapid-fire launches
- **Slower**: Increase to `2.0` to watch agents start one by one

## Tips

- **Refresh the dashboard** while agents are running to see updates
- **Click agent cards** to see the full workflow modal
- **Check metrics** after all agents complete
- **Run multiple times** to see cumulative metrics

## Clean Up

To clean up output files:
```bash
rm -rf demos/dashboard/output/*
```

To reset metrics:
```bash
rm ~/.pyjama/metrics.json
```

## Architecture

This demo showcases:
- ✅ **Parallel agent execution**
- ✅ **Cross-process monitoring** (shared metrics)
- ✅ **Real-time dashboard updates**
- ✅ **Auto-registration of hooks**
- ✅ **ES5-compatible JavaScript** (works in all browsers)
- ✅ **Event delegation** for dynamic content

Enjoy exploring the Pyjama dashboard! 🚀
