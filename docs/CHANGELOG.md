# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
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