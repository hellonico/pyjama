# Pyjama Template System Guide

The Pyjama template system provides powerful variable interpolation for agent prompts, tool arguments, and custom templates. This guide covers all features and best practices.

## Table of Contents

- [Basic Syntax](#basic-syntax)
- [Context Variables](#context-variables)
- [Trace Access](#trace-access)
- [Filters](#filters)
- [Operators](#operators)
- [Advanced Features](#advanced-features)
- [Best Practices](#best-practices)
- [Examples](#examples)

---

## Basic Syntax

Templates use double curly braces `{{...}}` for variable interpolation:

```markdown
Hello {{name}}!
Project: {{params.project-dir}}
Status: {{last-obs.text}}
```

### Simple Variables

```clojure
;; Context
{:name "Alice"
 :count 42}

;; Template
"Hello {{name}}, count is {{count}}"

;; Result
"Hello Alice, count is 42"
```

---

## Context Variables

The template system has access to several context namespaces:

### 1. Agent Context (`ctx`)

Access any field in the agent context:

```clojure
{{ctx.project-dir}}
{{ctx.best-score}}
{{ctx.id}}
```

**Shorthand**: You can omit `ctx.` for top-level context fields:

```clojure
{{project-dir}}  ; Same as {{ctx.project-dir}}
{{best-score}}   ; Same as {{ctx.best-score}}
```

### 2. Parameters (`params`)

Access agent input parameters:

```clojure
{{params.project-dir}}
{{params.output-file}}
{{params.verbose}}
```

### 3. Last Observation (`last-obs` or `obs`)

Access the most recent step's observation:

```clojure
{{last-obs.text}}        ; The text output
{{last-obs.status}}      ; Status field
{{last-obs.files}}       ; Any custom field

; Backward compatible alias
{{obs.text}}             ; Same as {{last-obs.text}}
```

**New in v0.2.0+**: `last-obs` is now a first-class shorthand alongside `obs`.

### 4. Prompt

Access the current prompt:

```clojure
{{prompt}}
```

---

## Trace Access

Access previous step observations using the trace:

### Numeric Indexing

```clojure
; Positive indices (0-based)
{{trace[0].obs.text}}    ; First step
{{trace[1].obs.text}}    ; Second step
{{trace[2].obs.text}}    ; Third step

; Negative indices (from end)
{{trace[-1].obs.text}}   ; Last step
{{trace[-2].obs.text}}   ; Second to last
{{trace[-3].obs.text}}   ; Third to last
```

### Nested Data Access

```clojure
{{trace[0].obs.summary.total}}
{{trace[1].obs.results.score}}
{{trace[2].obs.files[0]}}
```

### Step-Aware Access (when available)

Some implementations support accessing trace by step name:

```clojure
{{trace[:discover].obs.text}}
{{trace[:analyze].obs.count}}
```

---

## Filters

Transform values using pipe filters:

### String Filters

```clojure
{{ctx.name | lower}}              ; Convert to lowercase
{{ctx.name | upper}}              ; Convert to UPPERCASE
{{ctx.name | trim}}               ; Trim whitespace
{{ctx.name | slug}}               ; Convert to URL-friendly slug
{{ctx.name | sanitize}}           ; Sanitize for filenames
{{ctx.description | truncate:50}} ; Truncate to 50 chars
```

### Examples

```clojure
; Input: "A Roaring: Twenties / Jazz Party!!"
{{ctx.title | slug}}
; Output: "a-roaring-twenties-jazz-party"

{{ctx.title | sanitize}}
; Output: "A_Roaring_Twenties__Jazz_Party"

{{ctx.title | truncate:10}}
; Output: "A Roaring:"
```

### Chaining Filters

```clojure
{{ctx.prompt | slug | truncate:60}}.pdf
; Result: "my-awesome-project.pdf"
```

---

## Operators

Perform calculations and logic within templates:

### Arithmetic

```clojure
{{+ 4 5}}                ; 9.0
{{- 10 7}}               ; 3.0
{{- 7}}                  ; -7.0 (unary)
{{* 6 7}}                ; 42.0
{{/ 10 4}}               ; 2.5
{{max 2 8 3}}            ; 8.0
{{min 2 8 3}}            ; 2.0
```

### With Variables

```clojure
{{+ [:ctx.id] 1}}                    ; Increment ID
{{- [:obs.final-score] 1}}           ; Decrement score
{{max [:ctx.best-score] [:obs.score]}} ; Take maximum
```

### Comparisons

```clojure
{{> 5 3}}                ; true
{{< 5 3}}                ; false
{{>= 5 5}}               ; true
{{= "x" "x"}}            ; true
```

### Logic

```clojure
{{and true 1 "ok"}}      ; true
{{or nil false "x"}}     ; true
{{not true}}             ; false
```

### Conditional (Ternary)

```clojure
{{? [:> [:obs.score] 100] "passed" "failed"}}
; If score > 100, return "passed", else "failed"

{{? [:> [:trace -1 :obs :final-score] [:ctx :best-score]]
    [:trace -2 :obs :result]
    [:ctx :best-lend-fn]}}
; If last score > best score, use new result, else keep existing
```

---

## Advanced Features

### 1. Nested Templates

Templates can contain references to other template variables:

```clojure
; Context with nested structure
{:last-obs {:summary {:files 100
                       :errors 0}}
 :trace [{:obs {:text "Step 1"}}
         {:obs {:text "Step 2"}}]}

; Template
"Files: {{last-obs.summary.files}}, Errors: {{last-obs.summary.errors}}"
```

### 2. Resource Templates

Load templates from resource files:

```clojure
; In agent EDN
{:generate-report
 {:prompt "resource:templates/report_template.md"}}
```

The template file can contain any template variables:

```markdown
# Report for {{params.project-dir}}

## Analysis Results
{{trace[0].obs.text}}

## Summary
{{last-obs.text}}
```

### 3. Custom Template Rendering

Use the `render-custom-template` tool for complex scenarios:

```clojure
{:prepare-prompt
 {:tool :render-custom-template
  :args {:template "my_template.md"}}}
```

The template receives full agent context (`ctx`) and parameters (`params`).

### 4. Tool Argument Rendering

All tool arguments are automatically rendered with `render-args-deep`:

```clojure
{:save-report
 {:tool :write-file
  :args {:path "{{params.output-file}}"
         :message "{{last-obs.text}}"}}}
```

---

## Best Practices

### 1. Use Descriptive Variable Names

```clojure
; Good
{{last-obs.analysis-complete}}
{{params.output-directory}}

; Avoid
{{last-obs.x}}
{{params.dir}}
```

### 2. Prefer `last-obs` for Clarity

```clojure
; Recommended (explicit)
{{last-obs.text}}

; Also works (backward compatible)
{{obs.text}}
```

### 3. Use Trace Indices Carefully

```clojure
; Good: Use negative indices for recent steps
{{trace[-1].obs.text}}  ; Last step
{{trace[-2].obs.text}}  ; Previous step

; Risky: Positive indices can break if steps are added
{{trace[5].obs.text}}   ; Fragile!
```

### 4. Handle Missing Values Gracefully

Templates return empty strings for missing keys:

```clojure
{{last-obs.missing-key}}  ; Returns "" (not error)
```

### 5. Keep Templates Readable

```clojure
; Good: Multi-line for complex templates
"""
# Analysis Report

## Discovery
{{trace[0].obs.text}}

## Analysis
{{trace[1].obs.text}}

## Summary
{{last-obs.text}}
"""

; Avoid: Long single-line templates
"Discovery: {{trace[0].obs.text}} Analysis: {{trace[1].obs.text}} ..."
```

---

## Examples

### Example 1: Simple Agent Prompt

```clojure
{:analyze-code
 {:prompt "Analyze the codebase in {{params.project-dir}} and provide insights."}}
```

### Example 2: Multi-Step Synthesis

```clojure
{:generate-report
 {:prompt """
Based on the following analysis:

1. Discovery: {{trace[0].obs.text}}
2. Dependencies: {{trace[1].obs.text}}
3. Architecture: {{trace[2].obs.text}}

Current status: {{last-obs.text}}

Generate a comprehensive report.
"""}}
```

### Example 3: Conditional Logic

```clojure
{:update-best
 {:tool :passthrough
  :args {:set {:best-score "{{max [:ctx.best-score] [:obs.score]}}"
               :best-result "{{? [:> [:obs.score] [:ctx.best-score]]
                                  [:obs.result]
                                  [:ctx.best-result]}}"}}}}
```

### Example 4: File Output with Template

```clojure
{:save-inventory
 {:tool :write-file
  :args {:path "{{params.output-file}}"
         :message "{{last-obs.text}}"}}}
```

### Example 5: Resource Template

```clojure
; Agent definition
{:generate-inventory
 {:prompt "resource:templates/software_versions_v2.md"}}
```

```markdown
<!-- templates/software_versions_v2.md -->
# Software Inventory

## File Structure
{{trace[1].obs.text}}

## Dependencies
{{trace[2].obs.text}}

## Systems Detected
{{trace[3].obs.text}}

## Summary
{{last-obs.text}}
```

### Example 6: Complex Workflow

```clojure
{:software-versions-agent
 {:steps
  {:discover
   {:tool :discover-codebase
    :args {:dir "{{project-dir}}"}}
   
   :analyze
   {:tool :analyze-dependencies
    :args {:files "{{last-obs.files}}"}}
   
   :generate-report
   {:prompt "resource:templates/report.md"}
   
   :save
   {:tool :write-file
    :args {:path "{{output-file}}"
           :message "{{last-obs.text}}"}}}}
```

---

## Testing Templates

Use the test utilities to verify template rendering:

```clojure
(require '[pyjama.io.template :as tpl])

(def ctx {:last-obs {:text "Test"}
          :trace [{:obs {:text "Step 1"}}]})
(def params {:project-dir "."})

(tpl/render-template "{{last-obs.text}}" ctx params)
; => "Test"

(tpl/render-args-deep {:path "{{params.project-dir}}"} ctx params)
; => {:path "."}
```

---

## Migration Guide

### From v0.1.x to v0.2.0+

**New Features:**
- `last-obs` is now a first-class shorthand (alongside `obs`)
- `render-custom-template` supports full context interpolation
- Improved nested template variable resolution

**Backward Compatibility:**
- All existing `{{obs.*}}` templates continue to work
- No breaking changes to existing agents

**Recommended Updates:**
```clojure
; Old (still works)
{{obs.text}}

; New (more explicit)
{{last-obs.text}}
```

---

## Troubleshooting

### Template Not Rendering

**Problem**: `{{last-obs.text}}` appears literally in output

**Solution**: Ensure the template is being processed by `render-template`:
- Check that the prompt is recognized as a template (contains `{{`)
- Verify context contains the expected data
- Use `render-args-deep` for tool arguments

### Missing Values

**Problem**: Empty output where value expected

**Solution**: Check the context structure:
```clojure
; Debug by printing context
(println "Context:" ctx)
(println "Last obs:" (:last-obs ctx))
```

### Trace Index Out of Bounds

**Problem**: `{{trace[5].obs.text}}` returns empty

**Solution**: Use negative indices or verify trace length:
```clojure
; Safer
{{trace[-1].obs.text}}  ; Last step
{{trace[-2].obs.text}}  ; Previous step
```

---

## Reference

### Complete Syntax

```clojure
; Variables
{{variable}}
{{ctx.field}}
{{params.field}}
{{last-obs.field}}
{{obs.field}}
{{prompt}}

; Nested access
{{ctx.nested.field}}
{{last-obs.summary.total}}
{{trace[0].obs.text}}
{{trace[-1].obs.score}}

; Filters
{{value | filter}}
{{value | filter:arg}}
{{value | filter1 | filter2}}

; Operators
{{+ a b}}
{{- a b}}
{{* a b}}
{{/ a b}}
{{max a b c}}
{{min a b c}}
{{> a b}}
{{< a b}}
{{>= a b}}
{{<= a b}}
{{= a b}}
{{and a b c}}
{{or a b c}}
{{not a}}
{{? condition true-val false-val}}

; Bracket paths
{{[:ctx.field]}}
{{[:params.field]}}
{{[:obs.field]}}
{{[:trace -1 :obs :text]}}
```

---

## See Also

- [Agent Development Guide](./AGENT_GUIDE.md)
- [Tool Auto-Registration](./TOOL_AUTO_REGISTRATION.md)
- [Loops Documentation](./LOOPS.md)
- [CLI Commands Reference](./CLI_COMMANDS_REFERENCE.md)
