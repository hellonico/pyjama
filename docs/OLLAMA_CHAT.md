# Ollama Interactive Chat CLI

The Pyjama Ollama CLI provides an interactive chat interface for conversing with local Ollama models directly from the command line.

## Quick Start

### Basic Usage

Start an interactive chat session with the default model (llama3.2):

```bash
clj -M:ollama
```

### Specify a Model

Chat with a specific model:

```bash
clj -M:ollama -m llama3.1
clj -M:ollama -m qwen3-coder
clj -M:ollama -m deepseek-r1
clj -M:ollama -m mistral
```

### Use a Remote Ollama Server

Connect to an Ollama instance running on a different machine:

```bash
clj -M:ollama -m llama3.2 -u http://192.168.1.100:11434
```

## Command-Line Options

The Ollama CLI supports the following options:

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--url URL` | `-u` | Ollama server URL | `http://localhost:11434` |
| `--model MODEL` | `-m` | Model to use | `llama3.2` |
| `--chat MODE` | `-c` | Enable chat mode | `false` (auto-enabled with `:ollama` alias) |
| `--stream STREAM` | `-s` | Enable streaming responses | `true` |
| `--images IMAGES` | `-i` | Image file(s) for vision models | `[]` |
| `--prompt PROMPT` | `-p` | Single prompt (non-chat mode) | - |
| `--help` | `-h` | Show help message | - |

## Usage Examples

### Interactive Chat Session

```bash
# Start chat
clj -M:ollama

> hello
Hello! How can I assist you today?

> write fibonacci in clojure
Here's a simple recursive implementation:
...

> exit
# Press Ctrl+C to exit
```

### Single Prompt (Non-Chat Mode)

For one-off questions without entering chat mode:

```bash
clj -M -m pyjama.cli.ollama -p "Explain Clojure in one sentence"
```

### Vision Models with Images

Use vision-capable models (like `llava`) with images:

```bash
clj -M -m pyjama.cli.ollama -c true -m llava -i path/to/image.jpg

> What's in this image?
```

### Multiple Images

```bash
clj -M -m pyjama.cli.ollama -c true -m llava \
  -i image1.jpg -i image2.jpg -i image3.jpg
```

## Environment Variables

You can set default values using environment variables:

```bash
# Set default Ollama URL
export OLLAMA_URL=http://192.168.1.100:11434

# Now you can omit the -u flag
clj -M:ollama -m llama3.2
```

## Advanced Usage

### Custom Model Options

For advanced control over model behavior, you can modify the code to pass options like:

- `temperature` - Controls randomness (0.0 to 1.0)
- `repeat_penalty` - Reduces repetition (1.0 to 2.0)
- `top_k` - Limits token selection
- `top_p` - Nucleus sampling threshold

Example (requires code modification):

```clojure
{:options {:temperature 0.7
           :repeat_penalty 1.2
           :top_k 40
           :top_p 0.9}}
```

### Programmatic Usage

You can also use the Ollama chat functionality programmatically in your Clojure code:

```clojure
(require '[pyjama.core :as p])

;; Single prompt
(p/ollama "http://localhost:11434" :chat
  {:model "llama3.2"
   :messages [{:role :user :content "Hello!"}]})

;; Streaming chat
(p/ollama "http://localhost:11434" :chat
  {:model "llama3.2"
   :stream true
   :messages [{:role :user :content "Tell me a story"}]}
  p/print-chat-tokens)
```

## Troubleshooting

### Connection Issues

If you get connection errors:

1. **Check Ollama is running**:
   ```bash
   ollama list
   ```

2. **Verify the URL**:
   ```bash
   curl http://localhost:11434/api/tags
   ```

3. **Check firewall settings** if using a remote server

### Model Not Found

If you get "model not found" errors:

```bash
# Pull the model first
ollama pull llama3.2

# Then try the chat
clj -M:ollama -m llama3.2
```

### Performance Issues

For better performance:

1. **Use smaller models** for faster responses (e.g., `llama3.2:1b`)
2. **Disable streaming** if you prefer complete responses: `-s false`
3. **Use GPU acceleration** (ensure Ollama is configured with GPU support)

## Tips and Best Practices

1. **Model Selection**:
   - `llama3.2` - Good general-purpose model
   - `qwen3-coder` - Optimized for code generation
   - `deepseek-r1` - Advanced reasoning capabilities
   - `llava` - Vision model for image analysis

2. **Conversation Context**:
   - The chat maintains conversation history within a session
   - Each new session starts fresh
   - Press Ctrl+C to exit and start a new session

3. **Resource Usage**:
   - Larger models require more RAM
   - Check available models: `ollama list`
   - Monitor system resources during chat

## Related Documentation

- **[API Examples](EXAMPLES.md)** - Comprehensive Ollama API examples
- **[README.dev.md](README.dev.md)** - Developer documentation
- **[Main README](../README.md)** - Project overview

## Changelog

### v0.3.0+
- Added `:ollama` alias for convenient chat access
- Fixed double printing bug in streaming chat
- Removed debug output from chat sessions
- Improved documentation

## Contributing

Found a bug or have a feature request? Please open an issue on the [GitHub repository](https://github.com/hellonico/pyjama).
