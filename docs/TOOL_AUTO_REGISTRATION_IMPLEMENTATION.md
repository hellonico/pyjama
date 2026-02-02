# Tool Auto-Registration Implementation Summary

## Problem Statement

Previously, users had to manually duplicate tool definitions in agent EDN files:

```clojure
;; In plane-client/src/plane_client/pyjama/tools.clj
(defn register-tools! []
  {:create-or-update-issue {:fn create-or-update-issue ...}
   :upload-attachments {:fn upload-attachments-tool ...}
   :list-work-items {:fn list-work-items-tool ...}})
```

```edn
;; In agent EDN - MANUAL DUPLICATION!
:tools
{:create-or-update-issue {:fn plane-client.pyjama.tools/create-or-update-issue}
 :upload-attachments {:fn plane-client.pyjama.tools/upload-attachments-tool}
 :list-work-items {:fn plane-client.pyjama.tools/list-work-items-tool}}
```

## Solution

### 1. New Registry System (`pyjama.tools.registry`)

Created a new namespace that provides:
- **Namespace Registration**: Auto-discover tools from namespaces with `register-tools!` functions
- **Wildcard Expansion**: Support `{:* namespace}` and `{:prefix/* namespace}` syntax in EDN
- **Manual Registration API**: For programmatic tool registration

### 2. Integration with Agent Core

Modified `pyjama.agent.core/call` to:
1. Detect wildcard patterns in `:tools` map
2. Expand wildcards before merging with global tools
3. Support both simple (`:*`) and prefixed (`:plane/*`) wildcards

### 3. Three Usage Patterns

#### Pattern 1: Simple Wildcard
```edn
:tools {:* plane-client.pyjama.tools}
```
Imports all tools with original names.

#### Pattern 2: Prefixed Wildcard (Recommended)
```edn
:tools {:plane/* plane-client.pyjama.tools
        :email/* email-client.tools.registry}
```
Imports tools with namespace prefixes to avoid conflicts.

#### Pattern 3: Mixed
```edn
:tools {:plane/* plane-client.pyjama.tools
        :custom-tool {:fn my-ns/my-fn}}
```
Combines wildcards with explicit tool definitions.

## Files Created/Modified

### New Files
1. `/Users/nico/cool/origami-nightweave/pyjama/src/pyjama/tools/registry.clj`
   - Core registry implementation
   - Wildcard expansion logic
   - Namespace auto-discovery

2. `/Users/nico/cool/origami-nightweave/pyjama/test/pyjama/tools/registry_test.clj`
   - Comprehensive test suite
   - All tests passing ✓

3. `/Users/nico/cool/origami-nightweave/pyjama/docs/TOOL_AUTO_REGISTRATION.md`
   - Complete user documentation
   - Usage examples
   - Migration guide
   - Troubleshooting

4. `/Users/nico/cool/pyjama-commercial/jetlag/jetlag-agent-wildcard-example.edn`
   - Working example using new syntax

### Modified Files
1. `/Users/nico/cool/origami-nightweave/pyjama/src/pyjama/agent/core.clj`
   - Added `pyjama.tools.registry` require
   - Integrated wildcard expansion in `call` function

## Key Features

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

## Benefits

1. **DRY Principle**: Define tools once in `register-tools!`
2. **Less Boilerplate**: No manual EDN mapping
3. **Namespace Safety**: Prefixed wildcards prevent naming conflicts
4. **Flexibility**: Mix wildcards with explicit tools
5. **Discoverability**: Auto-discovery for REPL exploration

## Testing

All tests pass:
```
Running tests in #{\"test\"}

Testing pyjama.tools.registry-test
✓ Registered 2 tools from pyjama.tools.registry-test

Ran 3 tests containing 20 assertions.
0 failures, 0 errors.
```

## Migration Path

### Before
```edn
:tools
{:watch-emails {:fn email-client.tools.registry/watch-emails}
 :create-or-update-issue {:fn plane-client.pyjama.tools/create-or-update-issue}
 :upload-attachments {:fn plane-client.pyjama.tools/upload-attachments-tool}}
```

### After
```edn
:tools
{:email/* email-client.tools.registry
 :plane/* plane-client.pyjama.tools}
```

Update step references:
- `:watch-emails` → `:email/watch-emails`
- `:create-or-update-issue` → `:plane/create-or-update-issue`

## Next Steps

1. **Update Existing Agents**: Migrate jetlag and other agents to use wildcard syntax
2. **Documentation**: Add to main Pyjama README
3. **Examples**: Create more showcase agents using the pattern
4. **Tooling**: Consider adding validation/linting for tool namespaces

## API Reference

### Registry Functions

```clojure
(require '[pyjama.tools.registry :as registry])

;; Register a namespace
(registry/register-namespace! 'plane-client.pyjama.tools)

;; Auto-discover all tool namespaces
(registry/auto-discover! #".*\.pyjama\.tools$")

;; Get all registered tools
(registry/all-tools)

;; List registered namespaces
(registry/list-registered-namespaces)

;; Get tools from specific namespace
(registry/get-namespace-tools 'plane-client.pyjama.tools)

;; Expand wildcards (used internally)
(registry/expand-wildcard-tools {:plane/* 'plane-client.pyjama.tools})
```

### Tool Namespace Convention

```clojure
(ns my-project.pyjama.tools)

(defn my-tool-1 [obs] ...)
(defn my-tool-2 [obs] ...)

(defn register-tools!
  "Required function - returns tool map"
  []
  {:my-tool-1 {:fn my-tool-1 :description "..."}
   :my-tool-2 {:fn my-tool-2 :description "..."}})
```

## Conclusion

The tool auto-registration system significantly improves the developer experience by:
- Eliminating manual duplication
- Providing clear namespace organization
- Supporting flexible import patterns
- Maintaining backward compatibility

All existing agents continue to work, while new agents can adopt the cleaner wildcard syntax.
