# Pyjama Loop Support

**Complete Guide to Declarative Loops in Pyjama Agents**

---

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Loop Syntax](#loop-syntax)
4. [Loop Context Variables](#loop-context-variables)
5. [Complete Examples](#complete-examples)
6. [Migration Guide](#migration-guide)
7. [Implementation Details](#implementation-details)
8. [Visualization](#visualization)
9. [Advanced Patterns](#advanced-patterns)

---

## Overview

Pyjama now supports a declarative `:loop` construct that simplifies iteration over collections. This eliminates the need for manual "Fetch-Pop-Loop" patterns and makes batch processing agents cleaner and more maintainable.

### Features

- **Automatic Iteration**: No need for manual pop tools or routing logic
- **Loop Context Variables**: Access current item, index, and count within loop body
- **Subgraph Execution**: Loop body runs as an isolated subgraph with full trace support
- **Collection Support**: Works with vectors, lists, and maps (iterates over values)

### Benefits Over Fetch-Pop-Loop

1. **Simpler EDN**: No manual routing logic or pop tools
2. **Clearer Intent**: The loop structure is explicit
3. **Better Context**: Loop variables are automatically available
4. **Trace Integrity**: Each iteration is properly recorded
5. **Less Error-Prone**: No risk of stale dataset bugs

---

## Quick Start

### Basic Loop Example

```clojure
{:batch-processor
 {:start :fetch-items
  :tools {:fetch-items {:fn my.ns/fetch-items-tool}
          :process-item {:fn my.ns/process-item-tool}}
  :steps
  {:fetch-items
   {:tool :fetch-items
    :next :process-all}
   
   :process-all
   {:loop-over [:obs :items]        ; Iterate over collection
    :loop-body :process-one          ; Process each item
    :next :done}
   
   :process-one
   {:tool :process-item
    :args {:id "{{loop-item.id}}"   ; Access current item
           :name "{{loop-item.name}}"
           :index "{{loop-index}}"}  ; Current index
    :next :done}}}}                  ; Return to loop
```

---

## Loop Syntax

### Loop Step Structure

```clojure
:loop-step-name
{:loop-over [:obs :items]        ; Path to collection to iterate over
 :loop-body :process-item         ; Step ID to execute for each item
 :next :after-loop}               ; Step to go to after loop completes
```

### Required Keys

- **`:loop-over`** - Path to collection (e.g., `[:obs :items]`)
- **`:loop-body`** - Step ID to execute for each item
- **`:next`** - Step to go to after loop completes

---

## Loop Context Variables

Within the loop body, you have access to special context variables:

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `:loop-item` | The current item being processed | `{:id 1 :name "Item 1"}` |
| `:loop-index` | Zero-based index of current item | `0`, `1`, `2`, ... |
| `:loop-count` | Total number of items in the collection | `5` |
| `:loop-remaining` | Number of items remaining (including current) | `3` |

### Accessing Loop Variables in Templates

```clojure
:process-item
{:prompt "Processing item {{loop-index}} of {{loop-count}}:\n\n{{loop-item.title}}\n\nRemaining: {{loop-remaining}}"}
```

For nested data access:

```clojure
{:tool :some-tool
 :args {:id "{{loop-item.id}}"
        :name "{{loop-item.name}}"
        :index "{{loop-index}}"}}
```

### Loop Results

After the loop completes, the `:last-obs` contains:

```clojure
{:status :ok
 :loop-count 5                    ; Total items processed
 :loop-results [...]              ; Vector of observations from each iteration
 :message "Processed 5 items"}
```

If the collection is empty:

```clojure
{:status :ok
 :loop-count 0
 :message "No items to process"}
```

---

## Complete Examples

### Example 1: Batch MR Review

#### Old Pattern (Fetch-Pop-Loop)

```clojure
{:gitlab-mr-review-all
 {:start :list-mrs
  :tools
  {:list-mrs {:fn gitlab/list-assigned-mrs-tool}
   :get-diff {:fn gitlab/get-mr-diff-tool}
   :save-review {:fn gitlab/save-review-tool}
   :pop-mr {:fn gitlab/pop-first-mr-tool}}  ; Manual pop tool needed
  
  :steps
  {:list-mrs
   {:tool :list-mrs
    :routes [{:when [:> [:obs :count] 0] :next :get-diff}
             {:else :done}]}
   
   :get-diff
   {:tool :get-diff
    :args {:project-id "{{last-obs.first-mr.project_id}}"
           :mr-iid "{{last-obs.first-mr.iid}}"}
    :next :generate-review}
   
   :generate-review
   {:prompt "Review this MR: {{trace.list-mrs.obs.first-mr.title}}"
    :next :save-review}
   
   :save-review
   {:tool :save-review
    :args {:review "{{last-obs.text}}"
           :mr-iid "{{trace.list-mrs.obs.first-mr.iid}}"}
    :next :pop-mr}
   
   :pop-mr
   {:tool :pop-mr
    :args {:mrs "{{trace.list-mrs.obs.mrs}}"}
    :routes [{:when [:> [:obs :count] 0] :next :get-diff}  ; Manual loop routing
             {:else :done}]}}}}
```

#### New Pattern (Declarative Loop)

```clojure
{:gitlab-mr-review-all
 {:start :list-mrs
  :tools
  {:list-mrs {:fn gitlab/list-assigned-mrs-tool}
   :get-diff {:fn gitlab/get-mr-diff-tool}
   :save-review {:fn gitlab/save-review-tool}}
   ; No pop tool needed!
  
  :steps
  {:list-mrs
   {:tool :list-mrs
    :next :review-all}
   
   :review-all
   {:loop-over [:obs :mrs]        ; Iterate over MRs from list-mrs
    :loop-body :review-one         ; Process each MR
    :next :done}
   
   :review-one
   {:tool :get-diff
    :args {:project-id "{{loop-item.project_id}}"  ; Access current MR
           :mr-iid "{{loop-item.iid}}"}
    :next :generate-review}
   
   :generate-review
   {:prompt "Review MR {{loop-index}} of {{loop-count}}:\n\nTitle: {{loop-item.title}}\n\nDiff:\n{{last-obs.diff}}"
    :next :save-review}
   
   :save-review
   {:tool :save-review
    :args {:review "{{last-obs.text}}"
           :mr-iid "{{loop-item.iid}}"}
    :next :done}}}}  ; Loop automatically continues to next item
```

### Example 2: Multi-Step Loop Body

```clojure
:process-all-items
{:loop-over [:obs :items]
 :loop-body :fetch-details      ; Start of subgraph
 :next :summarize}

:fetch-details
{:tool :get-details
 :args {:id "{{loop-item.id}}"}
 :next :analyze}

:analyze
{:prompt "Analyze: {{last-obs.data}}"
 :next :save}

:save
{:tool :save-result
 :args {:result "{{last-obs.text}}"
        :item-id "{{loop-item.id}}"}
 :next :done}  ; End of subgraph, returns to loop
```

### Example 3: Nested Loops

```clojure
:outer-loop
{:loop-over [:obs :projects]
 :loop-body :process-project
 :next :done}

:process-project
{:tool :get-issues
 :args {:project-id "{{loop-item.id}}"}
 :next :inner-loop}

:inner-loop
{:loop-over [:obs :issues]
 :loop-body :process-issue
 :next :done}  ; Returns to outer loop

:process-issue
{:prompt "Process issue {{loop-item.title}} from project {{ctx.loop-item.id}}"}
```

**Note**: In nested loops, the outer loop's `:loop-item` is available via `:ctx` path.

---

## Migration Guide

### Migration Steps

To migrate from Fetch-Pop-Loop to the new loop construct:

1. **Remove the pop tool** from your tools registry
2. **Replace the initial fetch step** with a simple tool call
3. **Add a loop step** that references the collection
4. **Update the body steps** to use `{{loop-item.*}}` instead of `{{last-obs.first-*}}`
5. **Remove manual routing logic** - the loop handles it automatically
6. **Remove trace indexing** - use `{{loop-item}}` instead of `{{trace.fetch.obs.first-item}}`

### Before and After Comparison

| Aspect | Fetch-Pop-Loop | Declarative Loop |
|--------|----------------|------------------|
| Pop Tool | Required | Not needed |
| Routing | Manual with `:when` conditions | Automatic |
| Item Access | `{{last-obs.first-item}}` | `{{loop-item}}` |
| Index Tracking | Manual counter needed | `{{loop-index}}` |
| Trace Access | `{{trace.step.obs.first-item}}` | `{{loop-item}}` |

---

## Implementation Details

### Core Changes

**File: `src/pyjama/agent/core.clj`**

1. **Updated `step-non-llm-keys`** (line 92-93)
   - Added `:loop`, `:loop-body`, and `:loop-over`

2. **Added Forward Declaration** (line 172)
   - `(declare run-loop)`

3. **Loop Execution Function** (lines 442-485)
   - `run-loop`: Handles loop iteration
   - Extracts collection using `get-path`
   - Supports sequential collections and maps
   - Creates loop context with special variables
   - Executes loop body as subgraph
   - Collects results in `:loop-results`

4. **Updated `run-step`** (lines 238-244)
   - Recognizes loop steps
   - Calls `run-loop` when detected

5. **Updated `visualize`** (lines 513-520)
   - Shows loop structure in ASCII diagrams

### Loop Body as Subgraph

The loop body (`:loop-body`) is executed as a complete subgraph, which means:

- It can contain multiple steps
- It has its own trace
- It runs from the specified step until it reaches `:done`
- The final observation is collected in `:loop-results`

### Collection Support

Works with:
- **Vectors**: `[item1 item2 item3]`
- **Lists**: `'(item1 item2 item3)`
- **Maps**: Iterates over values

### Performance Considerations

- Loop bodies are executed sequentially (not in parallel)
- Each iteration creates a new trace entry
- For very large collections (>100 items), consider using `:max-steps` to prevent runaway execution
- Use `:parallel` steps within the loop body for concurrent operations on each item

---

## Visualization

### ASCII Visualization

```bash
clj -M:pyjama visualize loop-demo-agent
```

Output:
```
[Flow] Diagram for: loop-demo-agent

   * create-items
     |
   * loop-all-items
     | [Loop Over: [:obs :items]]
     |-- Body: process-one-item
     | [End Loop]
     |
   * summarize
     |
   [Done]
```

### Mermaid Flowchart

```bash
# Print to stdout
clj -M:pyjama visualize-mermaid loop-demo-agent

# Save to file
clj -M:pyjama visualize-mermaid loop-demo-agent diagram.md
```

The Mermaid diagram shows:
- **Loop steps** in orange hexagons
- **Tool steps** in blue rectangles
- **LLM steps** in green rounded rectangles
- **Routing steps** in orange diamonds
- **Parallel steps** in purple double rectangles

---

## Advanced Patterns

### Loop with Conditional Processing

```clojure
:process-all
{:loop-over [:obs :items]
 :loop-body :check-item
 :next :done}

:check-item
{:routes [{:when [:= [:loop-item :status] "pending"] :next :process-item}
          {:else :skip-item}]}

:process-item
{:tool :process
 :args {:item "{{loop-item}}"}
 :next :done}

:skip-item
{:next :done}  ; Skip processing, return to loop
```

### Loop with Error Handling

```clojure
:process-all
{:loop-over [:obs :items]
 :loop-body :try-process
 :next :done}

:try-process
{:tool :process-item
 :args {:item "{{loop-item}}"}
 :routes [{:when [:= [:obs :status] "error"] :next :log-error}
          {:else :done}]}

:log-error
{:tool :log-error
 :args {:error "{{last-obs.error}}"
        :item-id "{{loop-item.id}}"}
 :next :done}  ; Continue to next item despite error
```

### Accumulating Results

The loop automatically accumulates all observations in `:loop-results`:

```clojure
:after-loop
{:prompt "Summarize these results:\n\n{{last-obs.loop-results}}\n\nProvide an overview of what was accomplished."}
```

---

## Compatibility

The loop construct is fully compatible with:

- ✅ Tool steps
- ✅ LLM steps  
- ✅ Routing logic within loop body
- ✅ Parallel execution within loop body
- ✅ Template rendering
- ✅ Trace access
- ✅ Existing Fetch-Pop-Loop agents (backward compatible)

---

## Future Enhancements

Planned features for loop support:

- **Loop filtering**: `:loop-filter` to skip certain items
- **Loop mapping**: `:loop-map` to transform items before processing
- **Early exit**: `:loop-break-when` condition
- **Parallel loops**: `:loop-parallel true` for concurrent iteration
- **Loop accumulator**: `:loop-reduce` for aggregating results

---

## Troubleshooting

### Empty Collection

If the collection is empty, the loop body is skipped and `:last-obs` contains:

```clojure
{:status :ok
 :loop-count 0
 :message "No items to process"}
```

### Accessing Nested Data

Use dot notation in templates:

```clojure
"{{loop-item.nested.field}}"
```

### Loop Not Executing

Check that:
1. `:loop-over` path is correct
2. Collection exists in context
3. Collection is not empty
4. `:loop-body` step ID exists

### Infinite Loop

Ensure loop body steps eventually reach `:done` or have proper routing to `:done`.

---

## Examples Repository

See working examples in:
- `examples/loop-demo-agents.edn` - Demo agents
- `jetlag/jetlag-agent-with-loop.edn` - Real-world example

---

## Summary

The declarative loop construct makes batch processing agents:
- **Simpler** - Less boilerplate code
- **Clearer** - Intent is explicit
- **Safer** - No manual state management
- **More maintainable** - Easier to understand and modify

For questions or issues, see the [Pyjama documentation](https://github.com/hellonico/pyjama).
