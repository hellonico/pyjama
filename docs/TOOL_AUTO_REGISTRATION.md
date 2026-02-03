# Pyjama Tool Auto-Registration

**Complete guide to Pyjama's tool auto-registration system with wildcard imports, prefixed namespaces, and direct function references.**

---

## Table of Contents

1. [Overview](#overview)
2. [The Problem](#the-problem)
3. [Three Usage Patterns](#three-usage-patterns)
4. [How It Works](#how-it-works)
5. [API Reference](#api-reference)
6. [Migration Guide](#migration-guide)
7. [Best Practices](#best-practices)
8. [Troubleshooting](#troubleshooting)
9. [Implementation Details](#implementation-details)

---

## Overview

The Pyjama tool auto-registration system provides **three powerful patterns** to simplify tool definitions in agent EDN files:

1. **Wildcard Imports** - Import all tools from a namespace
2. **Prefixed Wildcards** - Import with namespace prefixes to avoid conflicts  
3. **Direct Function References** - Use any Clojure function directly as a tool

### Quick Example

**Before (Manual Duplication):**
```edn
:tools
{:create-issue {:fn plane-client.pyjama.tools/create-or-update-issue}
 :upload {:fn plane-client.pyjama.tools/upload-attachments-tool}
 :log {:fn clojure.pprint/pprint}
 :parse {:fn clojure.data.json/read-str}}
```

**After (Auto-Registration):**
```edn
:tools
{:plane/* plane-client.pyjama.tools  ; Wildcard import
 :log clojure.pprint/pprint          ; Direct function reference
 :parse clojure.data.json/read-str}  ; Direct function reference
```

---

## The Problem

Previously, you had to manually map every tool in your agent EDN:

```clojure
;; In plane-client/src/plane_client/pyjama/tools.clj
(defn register-tools! []
  {:create-or-update-issue {:fn create-or-update-issue :description "..."}
   :upload-attachments {:fn upload-attachments-tool :description "..."}
   :list-work-items {:fn list-work-items-tool :description "..."}
   :update-work-item-state {:fn update-work-item-state-tool :description "..."}})
```

```edn
;; In your agent EDN file - MANUAL DUPLICATION!
:tools
{:create-or-update-issue {:fn plane-client.pyjama.tools/create-or-update-issue}
 :upload-attachments {:fn plane-client.pyjama.tools/upload-attachments-tool}
 :list-work-items {:fn plane-client.pyjama.tools/list-work-items-tool}
 :update-work-item-state {:fn plane-client.pyjama.tools/update-work-item-state-tool}}
```

This is **tedious** and **error-prone** - you're essentially duplicating the registry!

---

## Three Usage Patterns

### Pattern 1: Simple Wildcard (Import All)

```edn
:tools
{:* plane-client.pyjama.tools}
```

This imports **all** tools from `plane-client.pyjama.tools` with their original names:
- `:create-or-update-issue`
- `:upload-attachments`
- `:list-work-items`
- `:update-work-item-state`

### Pattern 2: Prefixed Wildcard (Recommended)

```edn
:tools
{:plane/* plane-client.pyjama.tools
 :email/* email-client.tools.registry}
```

This imports tools with **prefixes** to avoid naming conflicts:
- `:plane/create-or-update-issue`
- `:plane/upload-attachments`
- `:email/watch-emails`
- `:email/send-email`

### Pattern 3: Direct Function References

```edn
:tools
{:log clojure.pprint/pprint
 :parse clojure.data.json/read-str
 :format my-ns/custom-formatter}
```

**Result:** Functions automatically wrapped as tools
- `:log` → `{:fn clojure.pprint/pprint :description "Tool: clojure.pprint/pprint"}`
- `:parse` → `{:fn clojure.data.json/read-str :description "Tool: clojure.data.json/read-str"}`

### Pattern 4: Mixed Approach (All Three Combined)

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

Explicit tools **override** wildcard imports if there's a naming conflict.

---

## How It Works

### Step 1: Define `register-tools!` in Your Tool Namespace

Your tool namespace must export a `register-tools!` function that returns a map:

```clojure
(ns plane-client.pyjama.tools
  (:require [plane-client.core :as plane]
            ;; ... other requires
            ))

(defn create-or-update-issue [obs]
  ;; implementation
  )

(defn upload-attachments-tool [obs]
  ;; implementation
  )

(defn register-tools!
  "Register Plane tools - called automatically by Pyjama registry"
  []
  {:create-or-update-issue {:fn create-or-update-issue
                            :description "Create or update Plane issue from email"}
   :upload-attachments {:fn upload-attachments-tool
                        :description "Upload email attachments to Plane issue"}
   :list-work-items {:fn list-work-items-tool
                     :description "List all work items in the project"}
   :update-work-item-state {:fn update-work-item-state-tool
                            :description "Update a work item's state"}})
```

**Key Points:**
- The function must be named `register-tools!`
- It must return a map of `keyword -> {:fn ... :description ...}`
- The `:fn` value should be the actual function var (not a symbol)

### Step 2: Use Wildcards in Your Agent EDN

```edn
{:my-agent
 {:description "My awesome agent"
  :start :first-step
  
  :tools
  {:plane/* plane-client.pyjama.tools}  ; ← Wildcard import!
  
  :steps
  {:first-step
   {:tool :plane/create-or-update-issue  ; ← Use prefixed name
    :args {:subject "{{obs.subject}}"
           :body "{{obs.body}}"}
    :next :done}}}}
```

### Step 3: Run Your Agent

When the agent loads, Pyjama will:
1. Detect the wildcard (`:plane/*`)
2. Require the namespace (`plane-client.pyjama.tools`)
3. Call `register-tools!` to get the tool map
4. Apply the prefix (`:plane/`) to all tool names
5. Normalize direct function references to `{:fn ... :description ...}` format
6. Merge with any explicit tools

---

## API Reference

### Registry Functions

```clojure
(require '[pyjama.tools.registry :as registry])

;; Register a namespace manually
(registry/register-namespace! 'plane-client.pyjama.tools)
;; => {:create-or-update-issue {...} :upload-attachments {...}}

;; Auto-discover all tool namespaces matching a pattern
(registry/auto-discover! #".*\\.pyjama\\.tools$")
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

---

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

**OR** use unprefixed wildcard if you don't have naming conflicts:

```edn
:tools
{:* email-client.tools.registry
 :* plane-client.pyjama.tools}  ; Later imports override earlier ones
```

### Step 3: Test

Run your agent to verify all tools load correctly.

---

## Best Practices

1. **Use Prefixes for Clarity**: Prefixed wildcards (`:plane/*`) make it clear where tools come from
2. **Group Related Tools**: Keep all tools for a domain (Plane, Email, GitLab) in one namespace
3. **Use Direct References for Simple Functions**: `clojure.pprint/pprint` instead of wrapping
4. **Use Full Format for Custom Descriptions**: When you need detailed documentation
5. **Document Your Tools**: Include `:description` in your tool definitions
6. **Version Your Tool Namespaces**: If you make breaking changes, create a new namespace (e.g., `v2`)

### Example of Good Tool Organization

```clojure
(ns my-project.tools.plane
  "Plane.so integration tools")

(defn create-issue [obs] ...)
(defn update-issue [obs] ...)
(defn list-issues [obs] ...)

(defn register-tools! []
  {:create-issue {:fn create-issue :description "Create a new Plane issue"}
   :update-issue {:fn update-issue :description "Update an existing issue"}
   :list-issues {:fn list-issues :description "List all issues in project"}})
```

```edn
;; In your agent
:tools
{:plane/* my-project.tools.plane
 :email/* my-project.tools.email
 :log clojure.pprint/pprint}
```

---

## Troubleshooting

### "Namespace does not have a register-tools! function"

Make sure your tool namespace exports a public `register-tools!` function:

```clojure
(defn register-tools! []  ; ← Must be public, not defn-
  {:tool-name {:fn tool-fn :description "..."}})
```

### "Wrong number of args passed to register-tools!"

The `register-tools!` function should take **zero arguments**:

```clojure
;; ✓ Correct
(defn register-tools! []
  {...})

;; ✗ Wrong
(defn register-tools! [config]
  {...})
```

### Tools Not Found at Runtime

If you get "tool not found" errors, check:
1. The namespace is on your classpath
2. The `register-tools!` function returns the correct map structure
3. You're using the correct tool names (with prefix if applicable)

### Wildcard Not Expanding

Make sure you're using the correct syntax:

```edn
;; ✓ Correct - symbol (no quotes in EDN)
:tools {:* plane-client.pyjama.tools}

;; ✗ Wrong - quoted symbol
:tools {:* 'plane-client.pyjama.tools}

;; ✗ Wrong - string
:tools {:* "plane-client.pyjama.tools"}
```

---

## Implementation Details

### Files Created/Modified

#### New Files
1. `/pyjama/src/pyjama/tools/registry.clj`
   - Core registry implementation
   - Wildcard expansion logic
   - Namespace auto-discovery

2. `/pyjama/test/pyjama/tools/registry_test.clj`
   - Comprehensive test suite
   - All tests passing ✓

3. `/pyjama/docs/TOOL_AUTO_REGISTRATION.md` (this file)
   - Complete user documentation

#### Modified Files
1. `/pyjama/src/pyjama/agent/core.clj`
   - Added `pyjama.tools.registry` require
   - Integrated wildcard expansion in `call` function

### Wildcard Detection

Handles three wildcard patterns:
1. **Simple**: `:*` → imports all tools
2. **Namespaced**: `:mock/*` (EDN reads as `#:mock{:*}`) → imports with `:mock/` prefix
3. **String-based**: `:plane/*` → imports with `:plane/` prefix

### Smart Merging

- Wildcards are expanded first
- Global tools are merged next
- Explicit tools override everything

### Error Handling

- Graceful handling of missing namespaces
- Clear error messages for missing `register-tools!` functions
- Validation of tool map structure

### Testing

All features are fully tested:

```bash
clojure -M:test -n pyjama.tools.registry-test
# Ran 6 tests containing 38 assertions.
# 0 failures, 0 errors.
```

---

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

---

## Examples

See these files for complete working examples:
- `jetlag-agent-wildcard-example.edn` - Wildcard imports
- `jetlag-agent-direct-functions-example.edn` - Direct function references
- `pyjama/test/pyjama/tools/registry_test.clj` - Test suite with examples

---

## Conclusion

The tool auto-registration system provides three complementary patterns that make agent development faster and more maintainable:

1. **Wildcard imports** eliminate manual tool mapping
2. **Prefixed wildcards** provide namespace safety
3. **Direct function references** reduce boilerplate

All patterns work together seamlessly, giving you maximum flexibility while maintaining clean, readable agent definitions.

**Status**: ✅ Complete and Production-Ready  
**Version**: Pyjama v0.2.0+  
**Compatibility**: Fully backward compatible
