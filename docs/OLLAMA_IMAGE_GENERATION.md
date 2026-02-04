# Ollama Image Generation Support for Pyjama

## Summary

Successfully added experimental Ollama image generation support to the Pyjama API wrapper in Clojure.

## Implementation Details

### 1. Core Changes (`src/pyjama/core.clj`)

#### Added Image Generation Handler
- New function `print-generate-image-tokens` to handle image generation responses
- Supports both streaming (progress updates) and non-streaming modes
- Extracts base64-encoded image data from the `:image` field in responses

#### Added `:generate-image` Command
- Added to the `DEFAULTS` map with configuration:
  - Model: `x/z-image-turbo` (Alibaba's 6B parameter image generation model)
  - Default dimensions: 1024x768
  - Supports `width`, `height`, and `steps` parameters per Ollama's experimental API

#### Endpoint Mapping
- Maps `:generate-image` command to the `/api/generate` endpoint
  - This is required because Ollama's image generation uses the same endpoint as text generation
  - The API automatically detects image generation models

#### Streaming Support
- Added :generate-image case to the streaming handler switch
- Displays progress updates during generation (e.g., "Progress: 8/9")

### 2. Test Suite (`test/morning/ollama_image_generation_test.clj`)

Created comprehensive test coverage:

#### Test 1: `image-generation-test`
- Generates a 512x512 image with prompt "summer beach a la matisse"
- Validates the response contains base64-encoded image data
- Decodes and saves the image to `/tmp/summer_beach_matisse.png`
- Verifies the decoded image has valid data

#### Test 2: `image-generation-streaming-test`
- Tests streaming mode with progress updates
- Ensures result is returned even in streaming mode

### 3. Test Results

Both tests passed successfully:
```
Ran 2 tests containing 4 assertions.
0 failures, 0 errors.
```

Generated image:
- File: `/tmp/summer_beach_matisse.png`
- Size: 385KB
- Format: PNG image data, 512 x 512, 8-bit/color RGB

## Usage Example

```clojure
(require '[pyjama.core :as pj])

;; Generate an image
(def result
  (pj/ollama
    "http://localhost:11434"
    :generate-image
    {:model  "x/z-image-turbo"
     :prompt "summer beach a la matisse"
     :width  512
     :height 512
     :stream false}))

;; result contains base64-encoded PNG image data
;; Decode and save:
(require '[clojure.java.io :as io])
(import '[java.util Base64])

(let [decoder (Base64/getDecoder)
      byte-array (.decode decoder result)]
  (with-open [out (io/output-stream "output.png")]
    (.write out byte-array)))
```

## Requirements

1. Ollama must be installed and running
2. Image generation model must be pulled:
   ```bash
   ollama pull x/z-image-turbo
   ```

## Available Models

- `x/z-image-turbo` - Alibaba's 6B parameter model (recommended)
  - Supports photorealistic output
  - Can render bilingual text (English and Chinese)
  - Requires 12-16GB VRAM

## API Parameters

According to Ollama's experimental API:
- `model` - The image generation model name
- `prompt` - Text description of the image to generate
- `width` - Image width in pixels (optional, default varies by model)
- `height` - Image height in pixels (optional, default varies by model)
- `steps` - Number of generation steps (optional, experimental)
- `stream` - Enable streaming for progress updates

## Agent Integration

### Option 1: Tool Function

Use `pyjama.tools.image/generate-image` in agent EDN configurations:

```clojure
{:my-image-agent
 {:description "Generates images from prompts"
  :steps {:generate {:tool pyjama.tools.image/generate-image
                     :args {:prompt "{{user-prompt}}"
                            :width 512
                            :height 512
                            :output-path "/tmp/generated.png"}}}}}
```

### Option 3: Pipeline Function

Use `pyjama.tools.image/generate-and-process` for functional pipelines:

```clojure
(require '[pyjama.tools.image :as img])

;; Simple generation
(img/generate-and-process "sunset over mountains")

;; With custom processor
(img/generate-and-process "sunset"
  :width 1024
  :height 768
  :processor (fn [base64-data]
               ;; Process the image data
               (analyze-image base64-data)))

;; In a threading macro
(->> "beautiful landscape"
     (img/generate-and-process :width 1024)
     :image-data
     process-further)
```

### Batch Generation

Generate multiple images at once:

```clojure
(img/generate-batch ["sunset" "ocean" "mountains"]
  :output-dir "/tmp/images"
  :prefix "nature"
  :width 512
  :height 512
  :parallel? true)
```

### Helper Functions

```clojure
;; Decode base64 to bytes
(img/decode-image base64-string)

;; Save image to file
(img/save-image base64-data "/tmp/output.png")
```

See `examples/image-generation-agents.edn` for complete agent configurations.

## Notes

- Image generation is **experimental** and may change in future Ollama versions
- Generation can take several minutes depending on:
  - Image dimensions
  - Model size
  - Hardware capabilities
- Streaming mode provides progress updates but still takes similar total time
- The API uses the same `/api/generate` endpoint as text generation
- Base64-encoded images are returned in the `:image` field of the response
