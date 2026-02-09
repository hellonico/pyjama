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

**Interactive Chat:**
```bash
# Start chat with default model (llama3.2)
clj -M:ollama

# Specify a different model
clj -M:ollama -m llama3.1

# Use a remote Ollama server
clj -M:ollama -m qwen2.5 -u http://192.168.1.100:11434
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

## Secrets Management

Secure API credentials with the **[Secrets Library](https://github.com/hellonico/secrets)** (**100% open source**):

```clojure
(require '[secrets.core :as secrets])

;; Get secrets in your agents
(secrets/get-secret [:openai :api-key])     ; => "sk-..."
(secrets/get-secret [:gitlab :token])        ; => "glpat-..."
```

**File-based** (`secrets.edn`):
```clojure
{:openai {:api-key "sk-..."}
 :gitlab {:token "glpat-..." :url "https://gitlab.com"}}
```

**Vault integration** (open source):
```clojure
(vault/read-secret config "secret" "pyjama/openai")
;; => {:api-key "sk-..."}
```

**Environment-aware** staging/production separation.

👉 **[Full Documentation](https://github.com/hellonico/secrets)** | **[Quick Start](docs/SECRETS.md)**

## Agent Showcases

Full-stack example agents demonstrating Pyjama's capabilities:

### 🎨 [Image Generator Agent](https://github.com/hellonico/pyjama-agent-showcases/tree/main/image-generator-agent)
AI image generation with Ollama's Z-Image Turbo model. Full-stack ClojureScript app with real-time progress tracking and beautiful UI.
- Text-to-image generation
- Real-time HTTP polling  
- Custom dimensions (128×128 to 2048×2048)
- Modern gradient UI

### 🎬 [Movie Review Agent](https://github.com/hellonico/pyjama-agent-showcases/tree/main/movie-review-agent)
AI-powered movie analysis using TMDB API. CLI and web UI modes.
- TMDB API integration
- LLM-powered reviews
- Plot summaries & recommendations
- Web UI + CLI modes

### 📧 [Email Agents](https://github.com/hellonico/pyjama-agent-showcases/tree/main/email-agents)
Email automation with **pure EDN** (no Clojure code!). Watcher and sender agents.
- Email monitoring with batch processing
- LLM email composition
- IMAP/SMTP integration
- Declarative loops

**More showcases:** [pyjama-agent-showcases](https://github.com/hellonico/pyjama-agent-showcases)

## Documentation

- **[Ollama Interactive Chat](docs/OLLAMA_CHAT.md)** - Interactive CLI chat with local Ollama models
- **[Dashboard](docs/DASHBOARD.md)** - Real-time agent monitoring and visualization
- **[Secrets Management](docs/SECRETS.md)** - File-based and Vault secret storage
- **[Shell and Cron Tools](docs/SHELL_AND_CRON_TOOLS.md)** - Execute commands and schedule tasks
- **[Agent Examples](examples/)** - EDN files showing chaining, routing, loops
  - [chaining-llms.edn](examples/chaining-llms.edn) - Chain multiple LLM calls
  - [routing-agents.edn](examples/routing-agents.edn) - Route based on LLM results
  - [simple-loop-demo.edn](examples/simple-loop-demo.edn) - Batch processing
  - [system-monitor-example.edn](examples/system-monitor-example.edn) - Shell and cron tools demo
- **[API Examples](docs/EXAMPLES.md)** - Comprehensive Ollama/ChatGPT examples
- **[Loop Support](docs/LOOP_SUPPORT.md)** - Detailed loop documentation
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
