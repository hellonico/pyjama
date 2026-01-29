# Codebase Analyzer CLI Commands Reference

Quick reference for all available CLI commands in the codebase analyzer system.

---

## Core Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `clj -M:smart-analyzer` | `:smart-analyzer` | Interactive agent selection and execution |
| `clj -M:search` | `:search` | Interactive report search and browser |
| `clj -M:inspect check` | `:inspect` | System health check and agent listing |
| `clj -M:run smart` | `:run` | Alternative smart analyzer entry point |

---

## System Inspection Commands

| Command | Description | Output |
|---------|-------------|--------|
| `clj -M:inspect check` | Check system and list agents | Table format |
| `clj -M:inspect check -v` | Verbose system check with workflows | Detailed table |
| `clj -M:inspect check --json` | System check in JSON format | JSON |
| `clj -M:inspect tools` | List all available tools by category | Formatted list |
| `clj -M:inspect stats` | Show agent registry statistics | Summary stats |

---

## Report Search Commands

| Command | Description | UI |
|---------|-------------|-----|
| `clj -M:search` | Interactive report browser | FZF interface |
| `clj -M:search list` | List all reports | Table format |
| `clj -M:search list --json` | List reports in JSON | JSON |
| `clj -M:search stats` | Report statistics | Summary with counts |

### FZF Controls (Interactive Search)
- **Type** - Filter reports
- **ENTER** - View report in pager
- **SPACE** - Open report in default app
- **P** - Toggle preview pane
- **ESC** - Exit

---

## Smart Analyzer Commands

| Command | Description |
|---------|-------------|
| `clj -M:smart-analyzer` | Run smart analyzer (current directory) |
| `clj -M:smart-analyzer <project-dir>` | Analyze specific project |
| `clj -M:smart-analyzer <project-dir> <reports-dir>` | Custom project and reports directories |

### Smart Analyzer Workflow
1. **Select** agent via FZF search
2. **View** agent workflow diagram
3. **Configure** required inputs
4. **Execute** agent with real-time progress
5. **Review** via post-analysis menu

### Post-Analysis Menu
- **ENTER** - Start new analysis
- **v** - View report (less)
- **o** - Open report (default app)
- **q** - Quit

---

## Agent Registry Commands

| Command | Description | Output |
|---------|-------------|--------|
| `clj -M:run list` | List all agents | JSON array |
| `clj -M:run search <query>` | Search agents by ID or description | JSON results |
| `clj -M:run describe <agent-id>` | Show agent details | JSON object |
| `clj -M:run visualize <agent-id>` | Display agent workflow diagram | ASCII diagram |
| `clj -M:run run <agent-id> <json>` | Execute agent programmatically | Agent output |

---

## Common Usage Patterns

### Quick Analysis
```bash
# Analyze current project
clj -M:smart-analyzer

# Analyze specific project
clj -M:smart-analyzer ~/projects/my-app
```

### Search & Browse
```bash
# Browse all reports interactively
clj -M:search

# Get latest reports
clj -M:search list | tail -5
```

### System Management
```bash
# Check system health
clj -M:inspect check

# Get agent count
clj -M:inspect stats
```

### Automation & Scripting
```bash
# Export all agents
clj -M:run list > agents.json

# Find architecture agents
clj -M:run search architecture | jq '.[] | .id'

# Get agent inputs
clj -M:run describe project-purpose-analyzer | jq '.inputs'

# Run agent programmatically
clj -M:run run project-purpose-analyzer '{"project-dir":".", "output-file":"report.md"}'
```

### JSON Processing Examples
```bash
# List all agent IDs
clj -M:run list | jq -r '.[] | .id'

# Filter by type
clj -M:run list | jq '.[] | select(.type == "graph")'

# Count reports by agent
clj -M:search list --json | jq 'group_by(.agent) | map({agent: .[0].agent, count: length})'

# Find longest analyses
clj -M:search list --json | jq 'sort_by(.duration_sec) | reverse | .[0:5]'
```

---

## Environment Variables

| Variable | Purpose | Example |
|----------|---------|---------|
| `PYJAMA_IMPL` | Override LLM provider | `export PYJAMA_IMPL=openai` |
| `LLM_MODEL` | Override model | `export LLM_MODEL=gpt-4` |
| `debug` | Enable debug mode | `export debug=true` |

---

## Quick Reference Table

| Task | Command |
|------|---------|
| **Interactive Analysis** | `clj -M:smart-analyzer` |
| **Browse Reports** | `clj -M:search` |
| **System Check** | `clj -M:inspect check` |
| **List Agents** | `clj -M:run list` |
| **Search Agents** | `clj -M:run search <query>` |
| **View Agent** | `clj -M:run describe <id>` |
| **Run Agent** | `clj -M:run run <id> <json>` |

---

## Dependencies

Required external tools:
- **fzf** - Fuzzy finder for interactive selection
- **jq** - JSON processor for scripting

Installation:
```bash
# macOS
brew install fzf jq

# Linux
apt-get install fzf jq
```

---

## Output Formats

### Table Format
Default human-readable format with aligned columns, used by:
- `clj -M:inspect check`
- `clj -M:search list`

### JSON Format
Machine-readable format for scripting, available in:
- `clj -M:inspect check --json`
- `clj -M:search list --json`
- `clj -M:run list`
- `clj -M:run describe <id>`

---

**Last Updated**: 2026-01-30
