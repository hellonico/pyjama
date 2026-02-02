# Pyjama Tool Auto-Registration Guide

This guide explains how to use Pyjama's tool auto-registration system to eliminate manual tool mapping in agent EDN files.

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
;; In your agent EDN file
:tools
{:create-or-update-issue {:fn plane-client.pyjama.tools/create-or-update-issue}
 :upload-attachments {:fn plane-client.pyjama.tools/upload-attachments-tool}
 :list-work-items {:fn plane-client.pyjama.tools/list-work-items-tool}
 :update-work-item-state {:fn plane-client.pyjama.tools/update-work-item-state-tool}}
```

This is **tedious** and **error-prone** - you're essentially duplicating the registry!

## The Solution: Wildcard Tool Imports

Pyjama now supports **wildcard imports** that automatically load all tools from a namespace:

### 1. Simple Wildcard (Import All)

```edn
:tools
{:* plane-client.pyjama.tools}
```

This imports **all** tools from `plane-client.pyjama.tools` with their original names:
- `:create-or-update-issue`
- `:upload-attachments`
- `:list-work-items`
- `:update-work-item-state`

### 2. Prefixed Wildcard (Namespace Tools)

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

### 3. Mixed Approach (Wildcards + Custom Tools)

```edn
:tools
{:plane/* plane-client.pyjama.tools
 :email/* email-client.tools.registry
 :custom-notify {:fn my-ns/notify-slack}
 :custom-log {:fn my-ns/log-to-file}}
```

Explicit tools **override** wildcard imports if there's a naming conflict.

### 4. Direct Function References (NEW!)

You can now reference Clojure functions directly without the `{:fn ...}` wrapper:

```edn
:tools
{:plane/* plane-client.pyjama.tools
 
 ;; Direct function references - automatically wrapped!
 :log-event clojure.pprint/pprint
 :parse-json clojure.data.json/read-str
 :format-data my-ns/custom-formatter
 
 ;; Still works with full format when you want custom descriptions
 :notify {:fn my-ns/notify-slack :description "Send Slack notification"}}
```

**How it works:**
- Symbol references like `clojure.pprint/pprint` are automatically converted to `{:fn clojure.pprint/pprint :description "Tool: clojure.pprint/pprint"}`
- This makes it super easy to use any Clojure function as a tool
- You can still use the full `{:fn ... :description ...}` format when you want custom descriptions

**Benefits:**
- Less boilerplate for simple function wrappers
- Use any Clojure function directly
- Mix with wildcards and explicit tool definitions

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
5. Merge with any explicit tools

## Advanced Usage

### Manual Registration (Optional)

You can manually register namespaces in your REPL or startup code:

```clojure
(require '[pyjama.tools.registry :as registry])

;; Register a single namespace
(registry/register-namespace! 'plane-client.pyjama.tools)
;; => {:create-or-update-issue {...} :upload-attachments {...}}

;; Auto-discover all tool namespaces matching a pattern
(registry/auto-discover! #".*\.pyjama\.tools$")
;; => {plane-client.pyjama.tools 4, email-client.tools.registry 3}

;; Get all registered tools
(registry/all-tools)
;; => {:create-or-update-issue {...} :upload-attachments {...} :watch-emails {...}}
```

### Inspecting Registered Tools

```clojure
;; List all registered namespaces
(registry/list-registered-namespaces)
;; => (plane-client.pyjama.tools email-client.tools.registry)

;; Get tools from a specific namespace
(registry/get-namespace-tools 'plane-client.pyjama.tools)
;; => {:create-or-update-issue {...} :upload-attachments {...}}
```

## Migration Guide

### Before (Manual Mapping)

```edn
:tools
{:watch-emails {:fn email-client.tools.registry/watch-emails}
 :create-or-update-issue {:fn plane-client.pyjama.tools/create-or-update-issue}
 :upload-attachments {:fn plane-client.pyjama.tools/upload-attachments-tool}}
```

### After (Wildcard Import)

```edn
:tools
{:email/* email-client.tools.registry
 :plane/* plane-client.pyjama.tools}
```

Then update your step references:
- `:watch-emails` → `:email/watch-emails`
- `:create-or-update-issue` → `:plane/create-or-update-issue`
- `:upload-attachments` → `:plane/upload-attachments`

**OR** use unprefixed wildcard if you don't have naming conflicts:

```edn
:tools
{:* email-client.tools.registry
 :* plane-client.pyjama.tools}  ; Later imports override earlier ones
```

## Best Practices

1. **Use Prefixes for Clarity**: Prefixed wildcards (`:plane/*`) make it clear where tools come from
2. **Group Related Tools**: Keep all tools for a domain (Plane, Email, GitLab) in one namespace
3. **Document Your Tools**: Include `:description` in your tool definitions
4. **Version Your Tool Namespaces**: If you make breaking changes, create a new namespace (e.g., `v2`)

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

## Complete Example

See `jetlag-agent-wildcard-example.edn` for a full working example.
