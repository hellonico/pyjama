# FZF Preview Fix for Registry Agents

## Problem

When using the smart analyzer (`clj -M:pyjama smart`), the FZF preview window was failing for registry agents with a JSON parse error because:
1. The preview command used `clj -M:pyjama describe` which only works for local agents
2. Registry agents aren't in the local `agents.edn`, so the describe command failed

## Solution

Updated the FZF preview to use the **ASCII visualize command** instead of JSON output, and enhanced the `visualize` command to support registry agents.

### Changes Made

#### 1. Updated FZF Preview (`src/pyjama/runner.clj`)

**Before:**
```clojure
"--preview" (str "clj -M:pyjama describe $(echo {} | cut -d'|' -f1 ...) | jq .")
```

**After:**
```clojure
preview-script (str "agent_id=$(echo {} | cut -d'|' -f1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//');"
                    "clj -M:pyjama visualize \"$agent_id\" 2>/dev/null || "
                    "echo 'Loading agent definition...'")
```

**Benefits:**
- Shows ASCII workflow diagram instead of JSON
- Much more visual and informative
- Works for both local and registry agents

#### 2. Enhanced Visualize Command (`src/pyjama/cli/agent.clj`)

Updated `run-visualize-agent` to check the file registry if agent is not found locally:

```clojure
(defn run-visualize-agent
  [id]
  (let [agent-key (keyword id)
        ;; Try local registry first
        meta (pyjama/describe-agent agent-key)
        ;; If not found, try file registry
        meta (or meta
                 (try
                   (let [{:keys [id spec]} (registry/lookup-agent id)]
                     {:id id :spec spec})
                   (catch Exception _ nil)))]
    ;; ... visualize the agent
    ))
```

## Result

Now when you use the smart analyzer:

```bash
$ clj -M:pyjama smart
```

The FZF preview window shows:
- **Local agents**: ASCII workflow diagram from agents.edn
- **Registry agents**: ASCII workflow diagram from ~/.pyjama/registry/

### Example Preview Output

```
┌─────────────────────────────────────────┐
│ Agent: mermaid-diagram-generator        │
│ Type: graph                             │
│                                         │
│ Workflow:                               │
│   START                                 │
│     ↓                                   │
│   get-project-info                      │
│     ↓                                   │
│   generate-component-diagram            │
│     ↓                                   │
│   save-component-diagram                │
│     ↓                                   │
│   generate-sequence-diagram             │
│     ↓                                   │
│   ...                                   │
└─────────────────────────────────────────┘
```

## Testing

To verify the fix works:

```bash
# 1. Ensure you have a registry agent
clj -M:pyjama registry list

# 2. Launch smart analyzer
clj -M:pyjama smart

# 3. Search for the registry agent (e.g., "mermaid")
# 4. Observe the preview window shows the ASCII diagram
# 5. No more JSON parse errors!
```

## Technical Notes

- The `visualize` command now has a fallback mechanism
- It tries local registry first (fast)
- Falls back to file registry if not found (for registry agents)
- The FZF preview runs this command for each agent as you navigate
- Error output is suppressed (`2>/dev/null`) to keep preview clean

## Future Enhancements

Potential improvements:
- Cache visualizations to speed up preview
- Add color to ASCII diagrams in preview
- Show agent metadata (inputs, tools) in preview
- Add preview for templates as well
