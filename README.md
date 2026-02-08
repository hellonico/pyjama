# Pyjama

**Autonomous Agent Framework** + **Ollama/ChatGPT Client** for Clojure

Build multi-step AI workflows with declarative EDN configuration. Chain LLM calls, route based on results, monitor in real-time.

Related blog [posts](http://blog.hellonico.info/tags/pyjama/)

## Agent Framework

Create autonomous workflows that chain LLM calls and route based on results - all in simple EDN.

### Chaining LLM Calls

Build research workflows by chaining multiple LLM prompts:

```clojure
{:research-agent
 {:start :initial-research
  :steps
  {:initial-research
   {:prompt "Research: {{ctx.topic}}. Provide a brief overview."
    :next :deep-dive}

   :deep-dive
   {:prompt "Based on: {{last-obs}}
   
Provide detailed analysis with examples."
    :next :summarize}

   :summarize
   {:prompt "Synthesize into a blog post:
Overview: {{trace.0.obs}}
Analysis: {{trace.1.obs}}"
    :next :done}}}}
```

**Run it:**
```bash
clj -M -m pyjama.cli.agent run research-agent '{"topic": "Clojure agents"}'
```

### Routing Based on Results

Route workflow based on LLM analysis:

```clojure
{:sentiment-router
 {:start :analyze-sentiment
  :steps
  {:analyze-sentiment
   {:prompt "Analyze sentiment. Reply with: POSITIVE, NEGATIVE, or NEUTRAL
   
Text: {{ctx.text}}"
    :next :route-by-sentiment}

   :route-by-sentiment
   {:routes
    [{:when [:= [:obs] "POSITIVE"]
      :next :handle-positive}
     {:when [:= [:obs] "NEGATIVE"]
      :next :handle-negative}
     {:when [:= [:obs] "NEUTRAL"]
      :next :handle-neutral}]}

   :handle-positive
   {:prompt "Generate an enthusiastic response!"
    :next :done}
   
   :handle-negative
   {:prompt "Generate an empathetic, solution-focused response."
    :next :done}
   
   :handle-neutral
   {:prompt "Generate a balanced, informative response."
    :next :done}}}}
```

### Batch Processing with Loops

Process collections declaratively:

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

**Loop Variables:**
- `{{loop-item}}` - Current item
- `{{loop-index}}` - Zero-based index
- `{{loop-count}}` - Total items
- `{{loop-remaining}}` - Items remaining

### Live Dashboard 📊

Monitor agents in real-time with interactive Mermaid diagrams:

```bash
clj -M -m pyjama.agent.hooks.dashboard
open http://localhost:8090
```

See [docs/DASHBOARD.md](docs/DASHBOARD.md) for full documentation.

## Ollama & ChatGPT APIs

### Ollama

```clojure
(require '[pyjama.core])
(pyjama.core/ollama "http://localhost:11434" :generate 
  {:prompt "Explain Clojure in one sentence"})
```

### ChatGPT / OpenAI

```clojure
(require '[pyjama.openai :as openai])

; Using :chatgpt
(openai/chatgpt 
  {:messages [{:role "user" 
               :content "Explain Clojure in one sentence"}]})
```

## Documentation

- **[Dashboard](docs/DASHBOARD.md)** - Real-time agent monitoring and visualization
- **[Agent Examples](examples/)** - EDN files showing chaining, routing, loops
  - [chaining-llms.edn](examples/chaining-llms.edn) - Chain multiple LLM calls
  - [routing-agents.edn](examples/routing-agents.edn) - Route based on LLM results
  - [simple-loop-demo.edn](examples/simple-loop-demo.edn) - Batch processing
- **[API Examples](docs/EXAMPLES.md)** - Comprehensive Ollama/ChatGPT examples
- **[Loop Support](docs/LOOP_SUPPORT.md)** - Detailed loop documentation
- **[Image Generation](docs/OLLAMA_IMAGE_GENERATION.md)** - AI image generation
- **[Changelog](docs/CHANGELOG.md)** - Release history

## Installation

```clojure
; deps.edn
{:deps {hellonico/pyjama {:git/url "https://github.com/hellonico/pyjama"
                          :git/tag "v0.3.0"
                          :git/sha "78b0c77"}}}
```



## License

Copyright © 2024-2026 hellonico

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
