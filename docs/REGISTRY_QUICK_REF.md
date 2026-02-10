# Agent Registry - Quick Reference

## Commands

### Register an agent (without running it)
```bash
clj -M:pyjama registry register <agent-file.edn>
```

### List all registered agents
```bash
clj -M:pyjama registry list
```

### Lookup agent details
```bash
clj -M:pyjama registry lookup <agent-id>
```

### Remove an agent from registry
```bash
clj -M:pyjama registry remove <agent-id>
# Aliases:
clj -M:pyjama registry unregister <agent-id>
clj -M:pyjama registry delete <agent-id>
```

### Look up and run a registered agent
```bash
clj -M:pyjama lookup-run <agent-id> <json-inputs>
```

## Examples

```bash
# Register the mermaid diagram generator
clj -M:pyjama registry register examples/mermaid-diagram-generator.edn

# List what's in the registry
clj -M:pyjama registry list

# Run it with custom inputs
clj -M:pyjama lookup-run mermaid-diagram-generator '{"project-dir":".", "output-dir":"./diagrams"}'

# Remove it when done
clj -M:pyjama registry remove mermaid-diagram-generator
```

## Registry Location

All registered agents are stored in:
```
~/.pyjama/registry/
```

Each agent is a separate `.edn` file named after its ID.
