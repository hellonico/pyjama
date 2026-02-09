# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
### Added
- **Unified LLM Chat CLI**: New `:llm` alias for multi-provider interactive chat
  - Single interface for ChatGPT, Claude, DeepSeek, Gemini, OpenRouter, and Ollama
  - Quick start: `clj -M:llm` (defaults to ChatGPT)
  - Provider switching: `clj -M:llm -l claude`, `clj -M:llm -l deepseek`, etc.
  - Automatic secrets loading from `~/.secrets/secrets.edn` or Vault
  - Interactive and single-prompt modes
  - Vision model support with `-i` flag
  - Temperature control and custom system prompts
  - Comprehensive documentation in [docs/LLM_CHAT.md](docs/LLM_CHAT.md)
- **Ollama Interactive Chat CLI**: New `:ollama` alias for convenient chat access
  - Quick start: `clj -M:ollama`
  - Easy model switching: `clj -M:ollama -m qwen3-coder`
  - Remote server support: `clj -M:ollama -u http://192.168.1.100:11434`
  - Comprehensive documentation in [docs/OLLAMA_CHAT.md](docs/OLLAMA_CHAT.md)
  - Interactive chat examples in main README
- **Ollama Image Generation**: Auto-detect image generation mode with progress tracking
  - Smart mode detection: automatically uses `x/z-image-turbo` when output file is `.png`
  - Animated spinner with elapsed time during generation
  - Custom dimensions: `-w` for width, `-g` for height
  - Generation time summary displayed after completion
  - Example: `clj -M -m pyjama.cli.ollama -o art.png -w 1920 -g 1080 -p "Cyberpunk city"`
- **Markdown Output for Text**: Save Ollama text responses to markdown files
  - Use `-f markdown` for auto-generated filename
  - Use `-o file.md` for specific filename
  - Formatted with prompt and response sections
  - Example: `clj -M -m pyjama.cli.ollama -m llama3.2 -o response.md -p "Explain Clojure"`
- **Native Binary Compilation**: Compile Ollama CLI to standalone native executable
  - Build with `clj -M:native-cli` (~2 minutes compilation time)
  - Instant startup (no JVM warmup)
  - Standalone ~75MB executable with no dependencies
  - Production-ready for deployment
  - Full feature parity with JVM version
  - GraalVM native-image with type hints for reflection-free compilation

### Fixed
- **Double Printing Bug**: Fixed duplicate character output in Ollama chat streaming
  - Removed duplicate state update in `pyjama.state/ollama-chat`
  - Each token now appears only once in chat output
- **Debug Output**: Removed `pprint` debug statement that was cluttering chat console
- **Image File Saving**: Fixed bug where image generation claimed success but didn't save file
  - Now properly decodes base64 image data and writes to disk
  - File size and location verified after generation


## [0.3.0] - 2026-02-08
### Added
- **Live Dashboard Visualization**: Real-time agent monitoring with interactive Mermaid diagrams
  - Beautiful flowchart visualization of agent workflows
  - Real-time step highlighting with blue glow animation
  - Modal tabs for Steps and Diagram views
  - Past Runs tab separating active and completed agents
  - Currently Running metric in dashboard
  - Agent spec storage in shared metrics (`~/.pyjama/metrics.json`)
  - API endpoint: `/api/agent/{id}/diagram`
  - Mermaid.js client-side rendering with syntax escaping for complex conditions
  - ES5-compatible JavaScript for broad browser support
- **Shutdown Hook**: Proper agent completion tracking on Ctrl-C
- **Enhanced Logging**: Agent registration messages showing workflow definition status
- **Demo Agent**: Simple loop demo using only prompts (`:loop-demo` alias)
- **Cross-Process Metrics**: File-based persistence for multi-process agent tracking

### Changed
- Dashboard now reads from shared metrics file instead of in-memory state
- Agent specs now stored in metrics for visualization
- JSON key handling improved (keyword vs string consistency)

### Fixed
- Mermaid diagram edge label escaping for special characters
- Agent completion tracking when stopping with Ctrl-C
- Dashboard API now properly retrieves agent data from shared metrics

## [0.2.0] - 2026-01-XX
### Added
- **Loop Support**: Declarative `:loop` construct for batch processing agents
  - `:loop-over` - Specify collection path to iterate over
  - `:loop-body` - Define step to execute for each item
  - Loop context variables: `loop-item`, `loop-index`, `loop-count`, `loop-remaining`
  - Automatic iteration management without manual pop tools
  - Full trace support for each iteration
  - Support for nested loops
  - See [docs/LOOP_SUPPORT.md](docs/LOOP_SUPPORT.md) for complete documentation

### Changed
- `step-non-llm-keys` now includes `:loop`, `:loop-body`, and `:loop-over`
- Agent execution now supports loop steps alongside tool, parallel, and LLM steps

### Deprecated
- Manual "Fetch-Pop-Loop" pattern is now deprecated in favor of declarative loops
  - Old pattern still works but new agents should use `:loop` construct
  - Migration guide available in [docs/LOOP_SUPPORT.md](docs/LOOP_SUPPORT.md)

## [0.1.2] - 2024-12-21
- Personalities for ollama generate
- Embeddings support
- Clojure CLI scripting sample

## [0.1.1] - 2024-12-20
### Changed
- Documentation
- First Pyjama release