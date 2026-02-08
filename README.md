# Pyjama

**Multi-Provider LLM Client** + **Agent Framework** for Clojure

Switch transparently between **Ollama**, **ChatGPT**, **Claude**, **DeepSeek**, and **OpenRouter**. Chain LLM calls with declarative workflows.

Related blog [posts](http://blog.hellonico.info/tags/pyjama/)

## LLM Client - Any Provider

### Ollama (Local)

```clojure
(require '[pyjama.core :as p])

(p/ollama "http://localhost:11434" :generate 
  {:prompt "Explain Clojure in one sentence"})
```

### ChatGPT / OpenAI

```clojure
(require '[pyjama.openai :as openai])

(openai/chatgpt 
  {:messages [{:role "user" :content "Explain Clojure"}]})
```

### Claude / OpenRouter / DeepSeek

Same simple interface - just swap the provider. See [docs/EXAMPLES.md](docs/EXAMPLES.md) for all providers.

## Agent Framework - Chain LLM Calls

**Want to chain multiple LLM calls?** The Agent Framework lets you dynamically route between providers and steps.

### Quick Example

```clojure
{:my-agent
 {:start :analyze
  :steps
  {:analyze
   {:prompt "Analyze: {{ctx.input}}. Return: positive/negative/neutral"
    :next :route}
   
   :route
   {:routes [{:when [:= [:obs] "positive"] :next :celebrate}
             {:when [:= [:obs] "negative"] :next :sympathize}]
    :next :neutral-response}}}}
```

**Run it:**
```bash
clj -M -m pyjama.cli.agent run my-agent '{"input": "I love Clojure!"}'
```

### Real-World Examples

- **Chaining** - Research workflow with 5 LLM calls → [examples/chaining-llms.edn](examples/chaining-llms.edn)
- **Routing** - Sentiment-based responses → [examples/routing-agents.edn](examples/routing-agents.edn)
- **Loops** - Batch processing → [examples/simple-loop-demo.edn](examples/simple-loop-demo.edn)

**Full documentation:** [docs/LOOP_SUPPORT.md](docs/LOOP_SUPPORT.md)

### Live Dashboard 📊

Monitor running agents with interactive Mermaid diagrams:

```bash
clj -M -m pyjama.agent.hooks.dashboard
open http://localhost:8090
```

**Full guide:** [docs/DASHBOARD.md](docs/DASHBOARD.md)


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
