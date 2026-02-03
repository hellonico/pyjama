# Template System Quick Reference

Quick reference for Pyjama's template interpolation syntax.

## Basic Syntax

| Syntax | Description | Example |
|--------|-------------|---------|
| `{{variable}}` | Simple variable | `{{name}}` |
| `{{ctx.field}}` | Context field | `{{ctx.project-dir}}` |
| `{{params.field}}` | Parameter | `{{params.output-file}}` |
| `{{last-obs.field}}` | Last observation | `{{last-obs.text}}` |
| `{{obs.field}}` | Observation (alias) | `{{obs.text}}` |

## Trace Access

| Syntax | Description | Example |
|--------|-------------|---------|
| `{{trace[N].obs.field}}` | Nth step (0-based) | `{{trace[0].obs.text}}` |
| `{{trace[-N].obs.field}}` | Nth from end | `{{trace[-1].obs.text}}` |
| `{{trace[N].obs.nested.field}}` | Nested access | `{{trace[0].obs.summary.total}}` |

## Filters

| Filter | Description | Example |
|--------|-------------|---------|
| `lower` | Lowercase | `{{name \| lower}}` |
| `upper` | Uppercase | `{{name \| upper}}` |
| `trim` | Trim whitespace | `{{text \| trim}}` |
| `slug` | URL-friendly | `{{title \| slug}}` |
| `sanitize` | Filename-safe | `{{name \| sanitize}}` |
| `truncate:N` | Truncate to N chars | `{{text \| truncate:50}}` |

### Chaining

```clojure
{{ctx.prompt | slug | truncate:60}}.pdf
```

## Operators

### Arithmetic

| Operator | Description | Example |
|----------|-------------|---------|
| `+` | Addition | `{{+ 4 5}}` → `9.0` |
| `-` | Subtraction | `{{- 10 7}}` → `3.0` |
| `*` | Multiplication | `{{* 6 7}}` → `42.0` |
| `/` | Division | `{{/ 10 4}}` → `2.5` |
| `max` | Maximum | `{{max 2 8 3}}` → `8.0` |
| `min` | Minimum | `{{min 2 8 3}}` → `2.0` |

### With Variables

```clojure
{{+ [:ctx.id] 1}}
{{max [:ctx.best-score] [:obs.score]}}
```

### Comparisons

| Operator | Description | Example |
|----------|-------------|---------|
| `>` | Greater than | `{{> 5 3}}` → `true` |
| `<` | Less than | `{{< 5 3}}` → `false` |
| `>=` | Greater or equal | `{{>= 5 5}}` → `true` |
| `<=` | Less or equal | `{{<= 3 5}}` → `true` |
| `=` | Equal | `{{= "x" "x"}}` → `true` |

### Logic

| Operator | Description | Example |
|----------|-------------|---------|
| `and` | Logical AND | `{{and true 1 "ok"}}` → `true` |
| `or` | Logical OR | `{{or nil false "x"}}` → `true` |
| `not` | Logical NOT | `{{not true}}` → `false` |

### Conditional (Ternary)

```clojure
{{? condition true-value false-value}}

; Example
{{? [:> [:obs.score] 100] "passed" "failed"}}
```

## Common Patterns

### Agent Prompts

```clojure
{:analyze
 {:prompt "Analyze {{params.project-dir}} and report findings."}}
```

### Multi-Step Synthesis

```clojure
{:generate-report
 {:prompt """
Discovery: {{trace[0].obs.text}}
Analysis: {{trace[1].obs.text}}
Summary: {{last-obs.text}}
"""}}
```

### Tool Arguments

```clojure
{:save
 {:tool :write-file
  :args {:path "{{params.output-file}}"
         :message "{{last-obs.text}}"}}}
```

### Resource Templates

```clojure
{:generate
 {:prompt "resource:templates/my_template.md"}}
```

### Conditional Updates

```clojure
{:update-best
 {:tool :passthrough
  :args {:set {:best "{{? [:> [:obs.score] [:ctx.best-score]]
                          [:obs.result]
                          [:ctx.best-result]}}"}}}}
```

## Tips

✅ **Use `last-obs` for clarity** (new in v0.2.0+)
```clojure
{{last-obs.text}}  ; Explicit
{{obs.text}}       ; Also works (backward compatible)
```

✅ **Prefer negative trace indices**
```clojure
{{trace[-1].obs.text}}  ; Last step (safe)
{{trace[5].obs.text}}   ; Fragile if steps change
```

✅ **Chain filters for complex transformations**
```clojure
{{ctx.title | slug | truncate:60}}.pdf
```

✅ **Use bracket notation for variables in operators**
```clojure
{{+ [:ctx.id] 1}}
{{max [:ctx.score] [:obs.score]}}
```

⚠️ **Missing values return empty strings (no errors)**
```clojure
{{last-obs.missing-key}}  ; Returns ""
```

## See Full Guide

For detailed documentation, examples, and best practices, see [TEMPLATING_GUIDE.md](./TEMPLATING_GUIDE.md).
