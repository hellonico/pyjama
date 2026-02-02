# Tool Auto-Registration: Complete Feature Summary

## Overview

The Pyjama tool auto-registration system provides **three powerful patterns** to simplify tool definitions in agent EDN files:

1. **Wildcard Imports** - Import all tools from a namespace
2. **Prefixed Wildcards** - Import with namespace prefixes to avoid conflicts  
3. **Direct Function References** - Use any Clojure function directly as a tool

## Quick Examples

### Before (Manual Duplication)
```edn
:tools
{:create-issue {:fn plane-client.pyjama.tools/create-or-update-issue}
 :upload {:fn plane-client.pyjama.tools/upload-attachments-tool}
 :log {:fn clojure.pprint/pprint}
 :parse {:fn clojure.data.json/read-str}}
```

### After (Auto-Registration)
```edn
:tools
{:plane/* plane-client.pyjama.tools  ; Wildcard import
 :log clojure.pprint/pprint          ; Direct function reference
 :parse clojure.data.json/read-str}  ; Direct function reference
```

## Three Usage Patterns

### Pattern 1: Wildcard Imports

Import all tools from a namespace that has a `register-tools!` function:

```edn
:tools {:* plane-client.pyjama.tools}
```

**Result:** All tools imported with original names
- `:create-or-update-issue`
- `:upload-attachments`
- `:list-work-items`

### Pattern 2: Prefixed Wildcards (Recommended)

Import with namespace prefixes to avoid naming conflicts:

```edn
:tools
{:plane/* plane-client.pyjama.tools
 :email/* email-client.tools.registry}
```

**Result:** Tools imported with prefixes
- `:plane/create-or-update-issue`
- `:plane/upload-attachments`
- `:email/watch-emails`
- `:email/send-email`

### Pattern 3: Direct Function References

Reference any Clojure function directly:

```edn
:tools
{:log clojure.pprint/pprint
 :parse clojure.data.json/read-str
 :format my-ns/custom-formatter}
```

**Result:** Functions automatically wrapped as tools
- `:log` → `{:fn clojure.pprint/pprint :description "Tool: clojure.pprint/pprint"}`
- `:parse` → `{:fn clojure.data.json/read-str :description "Tool: clojure.data.json/read-str"}`

## Combining All Three Patterns

You can mix all patterns in a single agent:

```edn
:tools
{;; Wildcard imports
 :plane/* plane-client.pyjama.tools
 :email/* email-client.tools.registry
 
 ;; Direct function references
 :log clojure.pprint/pprint
 :parse clojure.data.json/read-str
 
 ;; Explicit tool definitions (with custom descriptions)
 :notify {:fn my-ns/notify-slack 
          :description "Send notification to Slack channel"}}
```

## Implementation Details

### Tool Namespace Convention

To use wildcard imports, your tool namespace must have a `register-tools!` function:

```clojure
(ns my-project.pyjama.tools)

(defn my-tool-1 [obs] 
  ;; Tool implementation
  {:result "..."})

(defn my-tool-2 [obs]
  ;; Tool implementation  
  {:result "..."})

(defn register-tools!
  "Required function - returns tool map"
  []
  {:my-tool-1 {:fn my-tool-1 :description "Description of tool 1"}
   :my-tool-2 {:fn my-tool-2 :description "Description of tool 2"}})
```

### Normalization Process

When the agent loads, Pyjama automatically:

1. **Detects wildcards** (`:*` or `:prefix/*`)
2. **Requires namespaces** and calls `register-tools!`
3. **Applies prefixes** to tool names
4. **Normalizes direct function references** to `{:fn ... :description ...}` format
5. **Merges everything** (explicit tools override wildcards)

### Supported Function Reference Formats

Direct function references support multiple formats:

```edn
:tools
{;; Symbol reference (most common)
 :tool-1 my-ns/my-fn
 
 ;; Already wrapped (no change)
 :tool-2 {:fn my-ns/my-fn :description "Custom description"}
 
 ;; Var reference (in Clojure code, not EDN)
 :tool-3 #'my-ns/my-fn}
```

All are normalized to: `{:fn ... :description ...}`

## API Reference

### Registry Functions

```clojure
(require '[pyjama.tools.registry :as registry])

;; Register a namespace manually
(registry/register-namespace! 'plane-client.pyjama.tools)
;; => {:create-or-update-issue {...} :upload-attachments {...}}

;; Auto-discover all tool namespaces matching a pattern
(registry/auto-discover! #".*\.pyjama\.tools$")
;; => {plane-client.pyjama.tools 4, email-client.tools.registry 3}

;; Get all registered tools
(registry/all-tools)
;; => {:create-or-update-issue {...} :upload-attachments {...}}

;; List registered namespaces
(registry/list-registered-namespaces)
;; => (plane-client.pyjama.tools email-client.tools.registry)

;; Get tools from specific namespace
(registry/get-namespace-tools 'plane-client.pyjama.tools)
;; => {:create-or-update-issue {...} :upload-attachments {...}}

;; Normalize a tool definition
(registry/normalize-tool-def 'my-ns/my-fn)
;; => {:fn my-ns/my-fn :description "Tool: my-ns/my-fn"}

;; Normalize a tools map
(registry/normalize-tools-map {:tool-1 'my-ns/fn-1 :tool-2 'my-ns/fn-2})
;; => {:tool-1 {:fn my-ns/fn-1 :description "..."}
;;     :tool-2 {:fn my-ns/fn-2 :description "..."}}

;; Expand wildcards (used internally by agent core)
(registry/expand-wildcard-tools {:plane/* 'plane-client.pyjama.tools})
;; => {:plane/create-or-update-issue {...} :plane/upload-attachments {...}}
```

## Benefits

### 1. DRY Principle
Define tools once in `register-tools!`, use everywhere

### 2. Less Boilerplate
```edn
;; Before: 4 lines per tool
:my-tool {:fn my-ns/my-tool}

;; After: 1 line per tool
:my-tool my-ns/my-tool
```

### 3. Namespace Safety
Prefixed wildcards prevent naming conflicts:
```edn
:plane/create-issue  ; vs
:gitlab/create-issue
```

### 4. Flexibility
Mix wildcards, direct references, and explicit definitions

### 5. Use Any Clojure Function
```edn
:log clojure.pprint/pprint
:parse clojure.data.json/read-str
:format clojure.string/upper-case
```

## Migration Guide

### Step 1: Update Tool Definitions

**Before:**
```edn
:tools
{:watch-emails {:fn email-client.tools.registry/watch-emails}
 :create-issue {:fn plane-client.pyjama.tools/create-or-update-issue}
 :log {:fn clojure.pprint/pprint}}
```

**After:**
```edn
:tools
{:email/* email-client.tools.registry
 :plane/* plane-client.pyjama.tools
 :log clojure.pprint/pprint}
```

### Step 2: Update Step References

Update tool names in your steps to use prefixes:

```edn
:steps
{:my-step
 {:tool :email/watch-emails  ; was :watch-emails
  :next :next-step}}
```

### Step 3: Test

Run your agent to verify all tools load correctly.

## Examples

See these files for complete working examples:
- `jetlag-agent-wildcard-example.edn` - Wildcard imports
- `jetlag-agent-direct-functions-example.edn` - Direct function references
- `pyjama/test/pyjama/tools/registry_test.clj` - Test suite with examples

## Testing

All features are fully tested:

```bash
clojure -M:test -n pyjama.tools.registry-test
# Ran 6 tests containing 38 assertions.
# 0 failures, 0 errors.
```

## Best Practices

1. **Use prefixed wildcards** for clarity: `:plane/*` vs `:*`
2. **Group related tools** in one namespace
3. **Use direct references** for simple Clojure functions
4. **Use full format** when you need custom descriptions
5. **Document your tools** in the `register-tools!` function

## Troubleshooting

### "Namespace does not have a register-tools! function"

Ensure your namespace exports a public `register-tools!` function:

```clojure
(defn register-tools! []  ; Must be public
  {:tool-name {:fn tool-fn :description "..."}})
```

### "Tool not found at runtime"

Check:
1. Namespace is on classpath
2. `register-tools!` returns correct map structure
3. Tool names match (with prefix if applicable)

### "Wrong number of args"

`register-tools!` must take zero arguments:

```clojure
;; ✓ Correct
(defn register-tools! [] {...})

;; ✗ Wrong  
(defn register-tools! [config] {...})
```

## Conclusion

The tool auto-registration system provides three complementary patterns that make agent development faster and more maintainable:

1. **Wildcard imports** eliminate manual tool mapping
2. **Prefixed wildcards** provide namespace safety
3. **Direct function references** reduce boilerplate

All patterns work together seamlessly, giving you maximum flexibility while maintaining clean, readable agent definitions.
