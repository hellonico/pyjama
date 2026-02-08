# Pyjama

A [Clojure](https://clojure.org/) client for [Ollama](https://ollama.com/)

Related blog [posts](http://blog.hellonico.info/tags/pyjama/)

## Quick Start

### Ollama API

```clojure
(require '[pyjama.core])
(def url "http://localhost:11434")

; Using :ollama directly
(pyjama.core/ollama url :generate 
  {:prompt "Explain what Clojure is in one sentence"})
```

### ChatGPT / OpenAI

```clojure
(require '[pyjama.openai :as openai])

; Using :chatgpt
(openai/chatgpt 
  {:messages [{:role "user" 
               :content "Explain what Clojure is in one sentence"}]})
```

**For comprehensive examples**, see [docs/EXAMPLES.md](docs/EXAMPLES.md)

## Live Dashboard 📊

Monitor your agents in real-time with beautiful Mermaid flowchart visualizations:

```bash
# Start dashboard
clj -M -m pyjama.agent.hooks.dashboard

# Run your agent
clj -M:your-agent

# Open browser
open http://localhost:8090
```

**Features:**
- Real-time step highlighting
- Interactive workflow diagrams
- Past runs tracking
- Cross-process metrics

## Agent Framework

Build autonomous, multi-step workflows using declarative EDN configuration with powerful loop support:

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

**Loop Context Variables:**
- `{{loop-item}}` - Current item
- `{{loop-index}}` - Zero-based index
- `{{loop-count}}` - Total items
- `{{loop-remaining}}` - Items remaining

## Documentation

- **[Examples](docs/EXAMPLES.md)** - Comprehensive Ollama API examples
- **[Agent Framework](docs/LOOP_SUPPORT.md)** - Loop support and agent patterns
- **[Dashboard](docs/CHANGELOG.md#030---2026-02-08)** - Live visualization features
- **[Image Generation](docs/OLLAMA_IMAGE_GENERATION.md)** - AI image generation guide
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
