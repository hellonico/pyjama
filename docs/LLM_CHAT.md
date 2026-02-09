# Unified LLM Chat CLI

The Pyjama unified LLM CLI provides an interactive chat interface for conversing with **any LLM provider** from a single command-line interface. Switch between ChatGPT, Claude, DeepSeek, Gemini, OpenRouter, and Ollama seamlessly.

## Quick Start

### ChatGPT (Default)

```bash
clj -M:llm
```

### Other Providers

```bash
# Claude
clj -M:llm -l claude

# DeepSeek  
clj -M:llm -l deepseek

# Gemini
clj -M:llm -l gemini

# OpenRouter
clj -M:llm -l openrouter

# Ollama
clj -M:llm -l ollama
```

## Command-Line Options

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--llm PROVIDER` | `-l` | LLM provider (chatgpt, claude, deepseek, gemini, openrouter, ollama) | `chatgpt` |
| `--model MODEL` | `-m` | Model to use (provider-specific) | Provider default |
| `--prompt PROMPT` | `-p` | Single prompt (non-interactive mode) | - |
| `--system SYSTEM` | `-s` | System prompt | - |
| `--temperature TEMP` | `-t` | Temperature (0.0 to 1.0) | - |
| `--images IMAGES` | `-i` | Image file(s) for vision models | `[]` |
| `--url URL` | `-u` | Custom API URL (for Ollama or self-hosted) | - |
| `--help` | `-h` | Show help message | - |

## Default Models

Each provider has a sensible default model:

| Provider | Default Model |
|----------|---------------|
| ChatGPT | `gpt-4o-mini` |
| Claude | `claude-3-5-sonnet-20241022` |
| DeepSeek | `deepseek-chat` |
| Gemini | `gemini-2.0-flash-exp` |
| OpenRouter | `anthropic/claude-3.5-sonnet` |
| Ollama | `llama3.2` |

## Usage Examples

### Interactive Chat Sessions

**ChatGPT:**
```bash
clj -M:llm

🤖 Pyjama LLM Chat - chatgpt
📦 Model: gpt-4o-mini
💬 Type your message and press Enter. Ctrl+C to exit.

> Hello! Explain Clojure in one sentence.
Clojure is a modern, functional Lisp dialect that runs on the JVM...
```

**Claude with Custom Model:**
```bash
clj -M:llm -l claude -m claude-3-opus-20240229

> Write a haiku about programming
```

**DeepSeek:**
```bash
clj -M:llm -l deepseek

> Explain recursion
```

**Gemini:**
```bash
clj -M:llm -l gemini

> What's the weather like?
```

**OpenRouter (Access Multiple Providers):**
```bash
# Use Claude via OpenRouter
clj -M:llm -l openrouter -m anthropic/claude-3.5-sonnet

# Use GPT-4 via OpenRouter
clj -M:llm -l openrouter -m openai/gpt-4-turbo

# Use Llama via OpenRouter
clj -M:llm -l openrouter -m meta-llama/llama-3.1-70b-instruct
```

**Ollama (Local Models):**
```bash
# Default local model
clj -M:llm -l ollama

# Specific model
clj -M:llm -l ollama -m qwen3-coder

# Remote Ollama server
clj -M:llm -l ollama -m llama3.2 -u http://192.168.1.100:11434
```

### Single Prompt Mode

For one-off questions without entering interactive mode:

```bash
# ChatGPT
clj -M -m pyjama.cli.llm -p "Explain functional programming"

# Claude
clj -M -m pyjama.cli.llm -l claude -p "Write a poem about AI"

# DeepSeek
clj -M -m pyjama.cli.llm -l deepseek -p "Debug this code: ..."
```

### Vision Models with Images

```bash
# ChatGPT with vision
clj -M -m pyjama.cli.llm -l chatgpt -m gpt-4o -i photo.jpg -p "What's in this image?"

# Claude with vision
clj -M -m pyjama.cli.llm -l claude -m claude-3-5-sonnet-20241022 -i diagram.png -p "Explain this diagram"

# Multiple images
clj -M -m pyjama.cli.llm -l chatgpt -m gpt-4o \
  -i image1.jpg -i image2.jpg -i image3.jpg \
  -p "Compare these images"
```

### Custom System Prompts

```bash
clj -M -m pyjama.cli.llm -l chatgpt \
  -s "You are a Clojure expert who explains concepts concisely" \
  -p "What are atoms?"
```

### Temperature Control

```bash
# More creative (higher temperature)
clj -M -m pyjama.cli.llm -l chatgpt -t 0.9 -p "Write a creative story"

# More focused (lower temperature)
clj -M -m pyjama.cli.llm -l chatgpt -t 0.1 -p "What is 2+2?"
```

## Secrets Configuration

The unified LLM CLI automatically loads API keys from your secrets configuration. You need to set up secrets for the providers you want to use.

### File-Based Secrets (`~/.secrets/secrets.edn`)

```clojure
{:open-ai-key "sk-..."
 :anthropic-api-key "sk-ant-..."
 :deepseek {:api-key "sk-..."}
 :gemini {:api-key "..."}
 :openrouter {:api-key "sk-or-..."}}
```

### Vault-Based Secrets

See [Secrets Management Documentation](SECRETS.md) for Vault integration.

### Required Secrets by Provider

| Provider | Secret Key | Format |
|----------|------------|--------|
| ChatGPT | `:open-ai-key` | `"sk-..."` |
| Claude | `:anthropic-api-key` | `"sk-ant-..."` |
| DeepSeek | `:deepseek` → `:api-key` | `{:api-key "sk-..."}` |
| Gemini | `:gemini` → `:api-key` | `{:api-key "..."}` |
| OpenRouter | `:openrouter` → `:api-key` | `{:api-key "sk-or-..."}` |
| Ollama | None (local) | - |

## Provider-Specific Features

### ChatGPT
- **Vision**: Use `gpt-4o` or `gpt-4-turbo` with `-i` flag
- **JSON Mode**: Available programmatically
- **Function Calling**: Available programmatically

### Claude
- **Vision**: Built-in with Claude 3+ models
- **Long Context**: Claude 3 supports 200K tokens
- **Artifacts**: Available in web interface

### DeepSeek
- **Code-Focused**: Optimized for programming tasks
- **Cost-Effective**: Lower pricing than GPT-4
- **Fast**: Quick response times

### Gemini
- **Multimodal**: Native image, video, and audio support
- **Long Context**: Up to 1M tokens
- **Free Tier**: Generous free usage

### OpenRouter
- **Multiple Providers**: Access to 100+ models
- **Unified API**: Single API key for all models
- **Fallback**: Automatic failover between providers

### Ollama
- **Local**: No API keys needed
- **Privacy**: Data stays on your machine
- **Custom Models**: Run any GGUF model

## Troubleshooting

### Authentication Errors

```
❌ Error: 401 Unauthorized
💡 Tip: Check your secrets configuration and API keys
```

**Solution:**
1. Verify your secrets file exists: `cat ~/.secrets/secrets.edn`
2. Check the API key format matches the provider requirements
3. Ensure the key hasn't expired

### Provider Not Available

```
❌ Error: Unknown pyjama-call implementation
```

**Solution:**
- Verify the provider name is correct (chatgpt, claude, deepseek, gemini, openrouter, ollama)
- Check for typos in the `-l` flag

### Connection Errors (Ollama)

```
❌ Error: Connection refused
```

**Solution:**
1. Start Ollama: `ollama serve`
2. Verify Ollama is running: `curl http://localhost:11434/api/tags`
3. Check the URL with `-u` flag if using remote server

### Model Not Found

```
❌ Error: Model not found
```

**Solution:**
- For Ollama: Pull the model first: `ollama pull llama3.2`
- For other providers: Check the model name in their documentation

## Tips and Best Practices

### 1. Choose the Right Provider

- **ChatGPT**: Best general-purpose, excellent for creative tasks
- **Claude**: Best for long documents, analysis, and coding
- **DeepSeek**: Best for code generation and debugging
- **Gemini**: Best for multimodal tasks and long context
- **OpenRouter**: Best for trying multiple models easily
- **Ollama**: Best for privacy and offline use

### 2. Model Selection

```bash
# For coding
clj -M:llm -l deepseek
clj -M:llm -l claude -m claude-3-5-sonnet-20241022

# For creative writing
clj -M:llm -l chatgpt -m gpt-4o

# For analysis
clj -M:llm -l claude -m claude-3-opus-20240229

# For cost-effective tasks
clj -M:llm -l chatgpt -m gpt-4o-mini
clj -M:llm -l ollama -m llama3.2
```

### 3. Conversation Context

- Each interactive session maintains conversation history
- Press Ctrl+C to exit and start fresh
- Use single prompt mode (`-p`) for stateless queries

### 4. Resource Management

- Streaming is enabled by default for real-time responses
- Use temperature control for consistent vs creative outputs
- Monitor API usage and costs for cloud providers

## Comparison with Provider-Specific CLIs

### Unified CLI (`:llm`)
✅ Single interface for all providers  
✅ Consistent command-line options  
✅ Easy provider switching  
✅ Secrets integration  

### Ollama-Specific CLI (`:ollama`)
✅ Ollama-optimized features  
✅ Image support for vision models  
✅ Streaming control  

**When to use which:**
- Use `:llm` when you want flexibility to switch providers
- Use `:ollama` when you're focused on local Ollama models

## Related Documentation

- **[Ollama Interactive Chat](OLLAMA_CHAT.md)** - Ollama-specific CLI
- **[API Examples](EXAMPLES.md)** - Programmatic API usage
- **[Secrets Management](SECRETS.md)** - Secrets configuration
- **[Main README](../README.md)** - Project overview

## Examples Gallery

### Code Generation
```bash
clj -M:llm -l deepseek -p "Write a Clojure function to calculate Fibonacci"
```

### Code Review
```bash
clj -M:llm -l claude -p "Review this code: (defn foo [x] (+ x 1))"
```

### Documentation
```bash
clj -M:llm -l chatgpt -p "Write docstrings for this function"
```

### Translation
```bash
clj -M:llm -l gemini -p "Translate 'Hello, world!' to Japanese"
```

### Creative Writing
```bash
clj -M:llm -l chatgpt -t 0.9 -p "Write a short story about AI"
```

## Contributing

Found a bug or have a feature request? Please open an issue on the [GitHub repository](https://github.com/hellonico/pyjama).

## Changelog

### v0.3.0+
- Added unified LLM CLI with multi-provider support
- Automatic secrets loading
- Interactive and single-prompt modes
- Vision model support
- Temperature control
- Custom system prompts
