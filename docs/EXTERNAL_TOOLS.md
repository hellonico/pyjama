# External Clojure Tool Integration - Complete Guide

## Overview

This guide demonstrates how to integrate external Clojure projects as tools in Pyjama agents. This allows you to:
- Use full Clojure projects with their own dependencies
- Keep tool implementations separate from the Pyjama core
- Work with GraalVM native-image by shelling out to `clj`

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ Pyjama Agent (EDN)                                          │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ :tools                                               │   │
│  │   {:greeter {:fn pyjama.tools.external/execute-     │   │
│  │               clojure-tool                           │   │
│  │              :args {:project-path "~/tools/greeter" │   │
│  │                     :namespace "greeter-tool.core"}}}│   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ shells out to
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ External Clojure Project                                    │
│  ~/tools/greeter/                                           │
│    ├── deps.edn          (dependencies)                     │
│    └── src/                                                 │
│        └── greeter_tool/                                    │
│            └── core.clj   (tool implementation)             │
│                                                             │
│  Accepts JSON on stdin:                                     │
│    {"function": "greet", "params": {"name": "Alice"}}       │
│                                                             │
│  Returns JSON on stdout:                                    │
│    {"status": "ok", "greeting": "Hello Alice!"}             │
└─────────────────────────────────────────────────────────────┘
```

## Step-by-Step Implementation

### 1. Create the External Tool Project

**Directory Structure:**
```
pyjama-tools/greeter-tool/
├── deps.edn
├── README.md
└── src/
    └── greeter_tool/
        └── core.clj
```

**deps.edn:**
```clojure
{:paths ["src"]
 :deps {org.clojure/clojure {:mvn/version "1.11.1"}
        cheshire/cheshire {:mvn/version "5.11.0"}}}
```

**src/greeter_tool/core.clj:**
```clojure
(ns greeter-tool.core
  (:require [cheshire.core :as json]))

(defn greet
  "Generate a personalized greeting"
  [{:keys [name language style]}]
  {:status :ok
   :greeting (format "Hello %s!" name)
   :metadata {:name name :language language :style style}})

(defn -main
  "CLI entry point - reads JSON from stdin, executes function, prints JSON to stdout"
  [& args]
  (let [input (json/parse-string (slurp *in*) true)
        function-name (:function input)
        params (:params input)
        result (case function-name
                 "greet" (greet params)
                 {:status :error
                  :message (str "Unknown function: " function-name)})]
    (println (json/generate-string result))))
```

### 2. Create the Pyjama Tool Executor

**src/pyjama/tools/external.clj:**
```clojure
(ns pyjama.tools.external
  "Execute external Clojure project tools"
  (:require [clojure.java.shell :as shell]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn execute-clojure-tool
  "Execute a function from an external Clojure project.
   
   Args:
   - project-path: Path to the Clojure project (with deps.edn)
   - namespace: Namespace containing the function
   - function: Function name to execute
   - params: Parameters to pass to the function"
  [{:keys [project-path namespace function params]}]
  
  (let [expanded-path (str/replace project-path #"^~" (System/getProperty "user.home"))
        input-data {:function function :params (or params {})}
        input-json (json/generate-string input-data)
        result (shell/sh "clj" "-M" "-m" namespace
                         :dir expanded-path
                         :in input-json)]
    
    (if (zero? (:exit result))
      (json/parse-string (:out result) true)
      {:status :error
       :message "Tool execution failed"
       :exit-code (:exit result)
       :stderr (:err result)})))
```

### 3. Create an Agent that Uses the External Tool

**examples/greeter-demo-agent.edn:**
```clojure
{:greeter-demo-agent
 {:description "Demo agent that uses an external Clojure tool"
  :start :greet-user
  :max-steps 10

  :inputs
  {:user-name {:type "string" :required true}}

  :tools
  {:greeter {:fn pyjama.tools.external/execute-clojure-tool
             :args {:project-path "~/cool/origami-nightweave/pyjama-tools/greeter-tool"
                    :namespace "greeter-tool.core"}}}

  :steps
  {:greet-user
   {:tool :greeter
    :args {:function "greet"
           :params {:name "{{user-name}}"
                    :language "english"
                    :style "enthusiastic"}}
    :next :summarize}

   :summarize
   {:prompt "The greeting was: {{last-obs.greeting}}
             
             Create a fun summary!"
    :stream true
    :terminal? true}}}}
```

### 4. Register and Run

```bash
# Register the agent
clj -M:pyjama registry register examples/greeter-demo-agent.edn

# Run it
clj -M:pyjama run greeter-demo-agent '{"user-name":"Alice"}'
```

## How It Works

1. **Agent Execution**: Pyjama loads the agent from the registry
2. **Tool Resolution**: When a step uses `:tool :greeter`, Pyjama finds the tool definition
3. **Shell Out**: `pyjama.tools.external/execute-clojure-tool` shells out to `clj -M -m greeter-tool.core`
4. **JSON Communication**: 
   - Input is sent as JSON on stdin
   - Output is received as JSON on stdout
5. **Result Integration**: The JSON result is parsed and becomes available as `{{last-obs}}`

## GraalVM Compatibility

This approach works with GraalVM native-image because:
- ✅ The Pyjama binary doesn't need to dynamically load code
- ✅ It shells out to `clj` which handles the external tool
- ✅ Communication is via JSON (simple data)
- ✅ No `eval` or `load-file` needed

**Trade-offs:**
- **Pro**: Works with any Clojure code and dependencies
- **Pro**: Tools can be updated without recompiling Pyjama
- **Con**: Slower than native code (JVM startup ~1-2s)
- **Con**: Requires `clj` to be installed

## Best Practices

### 1. Tool Interface Contract

Always follow this pattern:

**Input (stdin):**
```json
{
  "function": "function-name",
  "params": {
    "arg1": "value1",
    "arg2": "value2"
  }
}
```

**Output (stdout):**
```json
{
  "status": "ok",
  "result": "...",
  "metadata": {}
}
```

### 2. Error Handling

```clojure
(defn -main [& args]
  (try
    (let [input (json/parse-string (slurp *in*) true)
          result (execute-function input)]
      (println (json/generate-string result)))
    (catch Exception e
      (println (json/generate-string
                {:status :error
                 :message (.getMessage e)})))))
```

### 3. Streaming Output

For LLM steps that should stream to terminal:

```clojure
:final-step
{:prompt "Generate a summary..."
 :stream true      ;; Enable streaming
 :terminal? true}  ;; Mark as terminal step
```

## Example: GitLab Client Integration

For a real-world example with external dependencies:

```clojure
;; Agent definition
{:gitlab-agent
 {:tools
  {:gitlab {:fn pyjama.tools.external/execute-clojure-tool
            :args {:project-path "~/pyjama-tools/gitlab-client"
                   :namespace "gitlab-client.core"}}}
  
  :steps
  {:create-mr
   {:tool :gitlab
    :args {:function "create-merge-request"
           :params {:title "{{mr-title}}"
                    :source-branch "{{branch}}"
                    :target-branch "main"}}}}}}
```

The gitlab-client project can have its own `deps.edn` with dependencies like `clj-http`, `cheshire`, etc.

## Performance Considerations

- **JVM Startup**: ~1-2 seconds per tool call
- **Mitigation**: Use for I/O-bound operations (API calls, file processing) where network latency dominates
- **Alternative**: For CPU-intensive tools, consider compiling them into the Pyjama binary

## Future Enhancements

Potential improvements:
1. **Babashka Support**: Use `bb` instead of `clj` for faster startup (~50ms)
2. **Tool Caching**: Keep a long-running JVM process for repeated calls
3. **Hybrid Mode**: Compile frequently-used tools into the binary, shell out for others

## Summary

External Clojure tool integration provides a powerful way to extend Pyjama agents while maintaining GraalVM compatibility. It's ideal for:
- Tools with complex dependencies
- Reusable components across projects
- Rapid prototyping without recompilation
- Integration with existing Clojure codebases
