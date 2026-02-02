# Pyjama System Inspection Module

The `pyjama.inspect` module provides comprehensive system inspection and monitoring capabilities for the Pyjama agent ecosystem.

## Features

- **Agent Registry Analysis**: List and describe all available agents
- **Tool Inventory**: Categorize and display registered tools
- **Beautiful Terminal Output**: ANSI colors and Unicode emojis for enhanced readability
- **Multiple Output Modes**: Human-readable or JSON for scripting
- **Verbose Mode**: Detailed agent information including inputs, steps, and workflows
- **Programmatic API**: Use functions directly in your Clojure code

## CLI Usage

### Basic System Check

```bash
clj -M:inspect check
```

Output:
```
╔════════════════════════════════════════════════════════════════╗
║                 PYJAMA - SYSTEM CHECK                       ║
╚════════════════════════════════════════════════════════════════╝

🤖 Loading agents...
✓ Found 31 agents

═══════════════════════════════════════════════════════════════
🤖 AGENTS OVERVIEW
═══════════════════════════════════════════════════════════════

AGENT ID                            DESCRIPTION
───────────────────────────────────────────────────────────────
antipatterns-agent                  Identifies engineering anti-patterns...
codebase-analyzer                   Performs comprehensive parallel analysis...
...
```

### Verbose Mode

```bash
clj -M:inspect check -v
```

Shows detailed information for each agent:
- Description
- Required and optional inputs with types and defaults
- Complete workflow with step-by-step transitions
- Agent type and max steps configuration

### JSON Output

```bash
clj -M:inspect check --json
```

Returns structured JSON data for integration with other tools.

### List Tools

```bash
clj -M:inspect tools
```

Groups tools into categories:
- Git Tools (git-related operations)
- Code Analysis Tools (discovery, parsing, analysis)
- Retrieval Tools (search, classify, pick)
- Template & I/O Tools (read, write, template rendering)
- Other Tools

### Agent Statistics

```bash
clj -M:inspect stats
```

Shows aggregate statistics about the agent registry.

## Programmatic API

### Check System

```clojure
(require '[pyjama.inspect :as inspect])

;; Basic check
(inspect/check-system)

;; Verbose with custom tools file
(inspect/check-system 
  :verbose? true 
  :tools-file "path/to/tools.edn")
```

Returns:
```clojure
{:agents [{:id :agent-id 
           :description "..."
           :type :graph
           :spec {...}}]
 :tools [{:category :git 
          :tools ["git-analyze" "git-hotspots"]}]
 :counts {:agents 31 :tools 25}}
```

### List Tools

```clojure
(inspect/list-tools "resources/agents/common-tools.edn")
```

Returns:
```clojure
{:total 25
 :categories {:git ["git-analyze" "git-hotspots"]
              :analysis ["discover-codebase" "analyze-complexity"]
              :io ["read-files" "write-file"]}
 :counts {:git 5 :analysis 12 :io 6 :other 2}}
```

### Agent Statistics

```clojure
(inspect/agent-stats)
```

Returns:
```clojure
{:total 31
 :by-type {:graph 29 :simple 2}
 :agents [{:id :agent-id :type :graph :description "..."}]}
```

## Tool Categorization

Tools are automatically categorized based on naming patterns:

| Category   | Pattern                                      |
|------------|----------------------------------------------|
| Git        | `git`, `pr`, `branch`                        |
| Analysis   | `discover`, `codebase`, `analyze`, `extract` |
| Retrieval  | `pick`, `retrieve`, `search`, `classify`     |
| I/O        | `template`, `write`, `read`, `notify`        |
| Other      | Everything else                              |

## Color Scheme

The terminal output uses ANSI colors for better readability:

- **Cyan**: Agent/tool names, primary identifiers
- **Yellow**: Types, tool names in workflows
- **Green**: Success messages, start steps
- **Red**: Required inputs
- **Gray**: Optional inputs, secondary information
- **Purple**: Section headers, LLM steps
- **White**: Important metadata values

## Integration Examples

### Custom Analysis Tool

```clojure
(defn analyze-project-health []
  (let [stats (inspect/agent-stats)
        tools (inspect/list-tools "resources/agents/common-tools.edn")]
    {:agent-count (:total stats)
     :tool-count (:total tools)
     :coverage (/ (:tool-count) (:agent-count))}))
```

### CI/CD Health Check

```bash
#!/bin/bash
# Verify agent registry is healthy
RESULT=$(clj -M:inspect check --json)
AGENT_COUNT=$(echo "$RESULT" | jq '.counts.agents')

if [ "$AGENT_COUNT" -lt 1 ]; then
  echo "Error: No agents found!"
  exit 1
fi

echo "✓ Found $AGENT_COUNT agents"
```

## Architecture

### Module Structure

```
pyjama/
├── src/pyjama/
│   ├── inspect.clj           # Core inspection logic
│   └── cli/
│       └── inspect.clj       # CLI wrapper
```

### Key Functions

- **Color Utilities**: ANSI color codes and emoji helpers
- **Tool Analysis**: Load, extract, and categorize tools from EDN
- **Agent Analysis**: Format and display agent metadata
- **Display Functions**: Formatted terminal output with colors
- **Public API**: Main entry points for system checks

## See Also

- [Pyjama Agent Framework](../README.md)
- [Agent Configuration Guide](./docs/agent-configuration.md)
- [Tool Development Guide](./docs/tool-development.md)
