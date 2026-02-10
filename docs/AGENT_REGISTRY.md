# Pyjama Agent Registry

## Overview

The Pyjama Agent Registry provides a local storage mechanism for agent definitions, allowing you to:
1. **Register** agent definitions without running them
2. **Look up and run** agents from the registry on demand

This is useful for maintaining a personal library of reusable agents that can be executed when needed.

## Registry Location

All registered agents are stored in:
```
~/.pyjama/registry/
```

Each agent is stored as a separate `.edn` file named after its agent ID.

## Commands

### 1. Register an Agent

Upload an agent definition to the local registry without executing it:

```bash
clj -M:pyjama registry register <agent-file.edn>
```

**Example:**
```bash
clj -M:pyjama registry register examples/mermaid-diagram-generator.edn
```

**Output:**
```
✅ Agent registered successfully!
   ID: :mermaid-diagram-generator
   Description: Simple example showing how to generate Mermaid diagrams using LLM
   Type: graph
   Location: /Users/nico/.pyjama/registry/mermaid-diagram-generator.edn
```

### 2. List Registered Agents

View all agents in your local registry:

```bash
clj -M:pyjama registry list
```

**Output:**
```
📂 Registered Agents (1 total)
   Location: /Users/nico/.pyjama/registry

  • :mermaid-diagram-generator
    Description: Simple example showing how to generate Mermaid diagrams using LLM
    Type: graph
```

### 3. Lookup an Agent

View details of a specific registered agent:

```bash
clj -M:pyjama registry lookup <agent-id>
```

**Example:**
```bash
clj -M:pyjama registry lookup mermaid-diagram-generator
```

### 4. Remove an Agent

Remove an agent from the registry:

```bash
clj -M:pyjama registry remove <agent-id>
# or use aliases:
clj -M:pyjama registry unregister <agent-id>
clj -M:pyjama registry delete <agent-id>
```

**Example:**
```bash
clj -M:pyjama registry remove mermaid-diagram-generator
# or
clj -M:pyjama registry unregister mermaid-diagram-generator
# or
clj -M:pyjama registry delete mermaid-diagram-generator
```

### 5. Lookup and Run an Agent

Look up an agent from the registry and execute it with provided inputs:

```bash
clj -M:pyjama lookup-run <agent-id> <json-inputs>
```

**Example:**
```bash
clj -M:pyjama lookup-run mermaid-diagram-generator '{"project-dir":".", "output-dir":"./diagrams"}'
```

This command:
1. Looks up the agent from `~/.pyjama/registry/`
2. Temporarily loads it into the runtime registry
3. Executes it with the provided inputs
4. Returns the result

## Use Cases

### Personal Agent Library
Maintain a collection of frequently-used agents:
```bash
# Register your favorite agents
clj -M:pyjama registry register my-agents/code-reviewer.edn
clj -M:pyjama registry register my-agents/doc-generator.edn
clj -M:pyjama registry register my-agents/test-creator.edn

# List them anytime
clj -M:pyjama registry list

# Run them when needed
clj -M:pyjama lookup-run code-reviewer '{"project-dir":"./src"}'
```

### Sharing Agents
Share agent definitions with team members:
```bash
# Export an agent
cp ~/.pyjama/registry/my-agent.edn /path/to/shared/agents/

# Import on another machine
clj -M:pyjama registry register /path/to/shared/agents/my-agent.edn
```

### Development Workflow
Test agents before adding them to your main `agents.edn`:
```bash
# Register for testing
clj -M:pyjama registry register experimental-agent.edn

# Test it
clj -M:pyjama lookup-run experimental-agent '{"input":"test"}'

# If it works, add to main agents.edn
# If not, remove it
clj -M:pyjama registry remove experimental-agent
```

## Implementation Details

### File Structure
Each registered agent is stored as:
```
~/.pyjama/registry/<agent-id>.edn
```

The file contains the full agent specification in EDN format:
```clojure
{:agent-id {:description "..."
            :start :step-name
            :steps {...}
            :tools {...}}}
```

### Runtime Behavior
When using `lookup-run`:
1. The agent spec is loaded from the registry file
2. It's temporarily added to the runtime `agents-registry` atom
3. The agent is executed using the standard execution path
4. The runtime registry is not persisted (only exists for that execution)

### Normalization
The registry handles both single-agent and multi-agent file formats:
- **Single-agent**: `{:description "..." :steps {...}}`
- **Multi-agent**: `{:agent-1 {...} :agent-2 {...}}`

Files are automatically normalized during registration.

## Comparison with agents.edn

| Feature | agents.edn | Registry |
|---------|-----------|----------|
| **Location** | Project-specific or global | `~/.pyjama/registry/` |
| **Loading** | Automatic on startup | On-demand via `lookup-run` |
| **Scope** | All agents loaded | Individual agents |
| **Use case** | Permanent agent definitions | Personal library, testing |
| **Management** | Manual file editing | CLI commands |

## Future Enhancements

Potential improvements:
- **Remote registry**: Fetch agents from a remote repository
- **Versioning**: Track multiple versions of the same agent
- **Tags/categories**: Organize agents by purpose
- **Search**: Search registry by description or tags
- **Export/import**: Bulk operations for sharing agent collections
