# Smart Analyzer Registry Integration

## Overview

The Pyjama Smart Analyzer now supports **both local agents and registry agents** in a unified interface!

## What Changed

### 1. **Unified Agent Discovery**
The smart analyzer (`clj -M:pyjama smart`) now shows agents from two sources:
- **Local agents** - Defined in `agents.edn` files
- **Registry agents** - Stored in `~/.pyjama/registry/` (tagged with `[registry]`)

### 2. **Automatic Registry Loading**
When you select a registry agent:
1. The agent is automatically loaded from `~/.pyjama/registry/`
2. It's temporarily added to the runtime registry
3. You can configure and execute it just like a local agent

### 3. **Visual Distinction**
Registry agents are clearly marked with a `[registry]` tag in the description, making it easy to distinguish them from local agents.

## Usage

### Step 1: Register an Agent
```bash
clj -M:pyjama registry register examples/mermaid-diagram-generator.edn
```

### Step 2: Launch Smart Analyzer
```bash
clj -M:pyjama smart
```

### Step 3: Search and Select
- Type to search through **all** available agents (local + registry)
- Registry agents will show `[registry]` in their description
- Press ENTER to select

### Step 4: Execute
- The agent will be loaded automatically if it's from the registry
- Configure inputs as usual
- Execute!

## Example Session

```bash
$ clj -M:pyjama smart

╔════════════════════════════════════════════════════════════════╗
║                                                                ║
║        🧠 PYJAMA SMART ANALYZER 🧠                             ║
║                                                                ║
║  Dynamic Agent Registry & Execution System                     ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝

📁 Target: /Users/nico/cool/origami-nightweave/pyjama

🔍 Search for an agent (Type to search, ENTER to select, ESC to exit)

# FZF shows:
project-purpose-analyzer | Analyzes project purpose and generates documentation
architecture-analyzer | Analyzes codebase architecture and patterns
mermaid-diagram-generator | Simple example showing how to generate Mermaid diagrams using LLM [registry]
...

# Select mermaid-diagram-generator (registry agent)

📦 Loaded from registry: mermaid-diagram-generator

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🤖 Selected: mermaid-diagram-generator
📝 Description: Simple example showing how to generate Mermaid diagrams using LLM [registry]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📋 Configuration:
  🔹 output-dir [default: ./reports/mermaid-diagram-generator-20260210-1400]: ./diagrams
  
🚀 Ready to launch!
   Agent: mermaid-diagram-generator
   Inputs: {"project-dir":".","output-dir":"./diagrams"}

Press ENTER to start (or Ctrl+C to cancel)...
```

## Benefits

### 1. **Seamless Integration**
No need to remember separate commands - all agents are accessible from one interface.

### 2. **Quick Testing**
Test registry agents without modifying your `agents.edn`:
```bash
# Register for testing
clj -M:pyjama registry register experimental-agent.edn

# Test via smart analyzer
clj -M:pyjama smart
# (search for experimental-agent)

# If it works, add to agents.edn
# If not, remove it
clj -M:pyjama registry remove experimental-agent
```

### 3. **Personal Agent Library**
Build a personal library of agents that you can access across all projects:
```bash
# Register your favorite agents once
clj -M:pyjama registry register ~/my-agents/code-reviewer.edn
clj -M:pyjama registry register ~/my-agents/doc-generator.edn

# Use them in any project via smart analyzer
cd ~/projects/any-project
clj -M:pyjama smart
# (all your registry agents are available!)
```

### 4. **Team Sharing**
Share agents with your team:
```bash
# Team member 1: Export agent
cp ~/.pyjama/registry/team-agent.edn /shared/agents/

# Team member 2: Import and use
clj -M:pyjama registry register /shared/agents/team-agent.edn
clj -M:pyjama smart
# (team-agent is now available!)
```

## Implementation Details

### Modified Files

**`src/pyjama/runner.clj`:**
- Added `pyjama.cli.registry` to requires
- Added `get-all-agents` function to merge local and registry agents
- Updated main loop to:
  - Call `get-all-agents` instead of `pyjama/list-agents`
  - Detect registry agents by their `:source` metadata
  - Automatically load registry agents into runtime before execution

### How It Works

1. **Discovery Phase:**
   ```clojure
   (defn- get-all-agents []
     (let [local-agents (pyjama/list-agents)
           registry-agents (registry/list-registered-agents)
           ...]
       (concat local-agents-marked registry-agents-formatted)))
   ```

2. **Selection Phase:**
   - User selects an agent via FZF
   - System checks if it's a registry agent

3. **Loading Phase (Registry Agents Only):**
   ```clojure
   (when is-registry-agent?
     (let [{:keys [id spec]} (registry/lookup-agent agent-id)]
       (swap! pyjama/agents-registry assoc id spec)))
   ```

4. **Execution Phase:**
   - Agent executes normally (same code path for both local and registry agents)

## Future Enhancements

Potential improvements:
- **Color coding** - Different colors for local vs registry agents in FZF
- **Source indicator** - Show source location in preview window
- **Auto-sync** - Automatically sync registry agents from a remote repository
- **Favorites** - Mark frequently-used registry agents as favorites
- **Categories** - Organize registry agents by category/tags

## Testing

To test the integration:

```bash
# 1. Register a test agent
clj -M:pyjama registry register examples/mermaid-diagram-generator.edn

# 2. Launch smart analyzer
clj -M:pyjama smart

# 3. Search for "mermaid"
# 4. Verify it shows with [registry] tag
# 5. Select and execute it
# 6. Verify it works correctly
```

## Summary

The smart analyzer now provides a **unified interface** for discovering and executing agents from both local and registry sources, making it easier to:
- Test new agents
- Share agents across projects
- Build personal agent libraries
- Collaborate with team members

All with zero configuration required - just register and go!
