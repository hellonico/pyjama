# Claude Integration

Pyjama now supports Claude AI through Anthropic's API. This integration provides access to Claude's advanced reasoning capabilities, vision features, and structured output.

## Setup

1. **Get an Anthropic API Key**: Sign up at [Anthropic Console](https://console.anthropic.com/) and get your API key.

2. **Set Environment Variable**:
   ```bash
   export ANTHROPIC_API_KEY="your-api-key-here"
   ```

## Usage

### Basic Usage

```clojure
(require '[pyjama.core :as pyjama])

;; Direct Claude call
(pyjama/call {:impl :claude
               :prompt "What is the capital of France?"})

;; With specific model
(pyjama/call {:impl :claude
               :prompt "Explain quantum computing"
               :model "claude-3-5-sonnet-20241022"})
```

### Streaming Responses

```clojure
;; Streaming response
(pyjama/call {:impl :claude
               :prompt "Write a short story about a robot"
               :stream true})
```

### Vision Support

```clojure
;; With image input
(pyjama/call {:impl :claude
               :prompt "What do you see in this image?"
               :image-path "path/to/image.jpg"})
```

### Structured Output

```clojure
(require '[pyjama.claude.core :as claude])

;; Get structured response
(let [schema {:type "object"
              :properties {:answer {:type "string"}
                          :confidence {:type "number"}}
              :required ["answer" "confidence"]}
      result (claude/get-structured-response
              "What is the capital of France? Answer with confidence level."
              schema)]
  (println result))
```

## Available Models

- `claude-3-5-sonnet-20241022` (default) - Latest Claude 3.5 Sonnet
- `claude-3-opus-20240229` - Claude 3 Opus
- `claude-3-sonnet-20240229` - Claude 3 Sonnet
- `claude-3-haiku-20240307` - Claude 3 Haiku

## Configuration Options

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `model` | string | `claude-3-5-sonnet-20241022` | Claude model to use |
| `max_tokens` | integer | `4096` | Maximum tokens in response |
| `temperature` | float | `0.7` | Response randomness (0.0-1.0) |
| `stream` | boolean | `false` | Enable streaming response |
| `image-path` | string | `nil` | Path to image for vision |

## Examples

See `examples/claude_example.clj` for complete working examples.

## Integration with Agents

You can use Claude with Pyjama's agent system:

```clojure
;; In agents.edn
{:claude-assistant {:impl :claude
                    :model "claude-3-5-sonnet-20241022"
                    :temperature 0.7}}

;; Usage
(pyjama/call {:id :claude-assistant
               :prompt "Help me solve this math problem"})
```

## Error Handling

The integration includes proper error handling for:
- Missing API key
- Invalid model names
- Network errors
- Rate limiting
- Malformed responses

## Differences from Other Providers

- Uses `x-api-key` header instead of `Authorization`
- Requires `anthropic-version` header
- Different message format for vision
- Structured output uses `tool_use` instead of `tool_calls` 