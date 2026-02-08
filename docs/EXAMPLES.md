# Pyjama Examples

This document contains comprehensive examples for using Pyjama with Ollama.

## Table of Contents
- [Basic Generation](#basic-generation)
- [Streaming](#streaming)
- [JSON Output](#json-output)
- [Structured Output](#structured-output)
- [Vision Models](#vision-models)
- [Chat Interface](#chat-interface)
- [Model Management](#model-management)
- [Embeddings](#embeddings)
- [Image Generation](#image-generation)

## Basic Generation

Simple text generation:

```clojure
(require '[pyjama.core])
(def url "http://localhost:11434")

; Basic generate
(pyjama.core/ollama 
  url
  :generate 
  {:prompt "What color is the sky at night and why?"})
```

## Streaming

Generate with streaming output:

```clojure
(pyjama.core/ollama
  url
  :generate
  {:prompt "What color is the sky at night and why?" :stream true})
```

## JSON Output

Generate using JSON format:

```clojure
(pyjama.core/ollama 
  url 
  :generate
    {
     :model "llama3.2"
     :prompt "What color is the sky at different times of the day? Respond using JSON"
     :format "json"
     :stream false
     })
; => {"daytime": "blue", "sunset": "orange-red", "dusk": "pink-orange", "night": "dark blue or black"}
```

## Structured Output

### JSON Structure

```clojure
(pyjama.core/ollama URL :generate
                    {:stream false :format structure
                     :model model
                     :prompt "Pyjama is 22 days old and is busy saving the world."}
                    :response)
; => {"age": 22, "available": true}
```

### EDN Structure

```clojure
(pyjama.core/ollama URL :generate
                    {:stream false
                     :format structure
                     :model model
                     :prompt "Pyjama is 22 days old and is busy saving the world."}
                    pyjama.core/structure-to-edn)
; => {:age 22, :available true}
```

## Vision Models

Analyze images with vision models:

```clojure
(pyjama.core/ollama 
  url 
  :generate
  {:model "llava"
   :prompt "what is in this picture?"
   :stream true
   :images [(pyjama.image/image-to-base64 "resources/cute_cat.jpg")]})
```

## Reproducible Output

Generate reproducible responses using seeds:

```clojure
(pyjama.core/ollama 
  url 
  :generate
 {:model "llama3.2"
  :prompt "Why is the sky blue?" 
  :options {:seed 123}})
```

## Chat Interface

Multi-turn conversations:

```clojure
(pyjama.core/ollama
  url
  :chat
  {:model "llama3.2" 
   :stream true 
   :message 
   {:role :user :content "Who is mario?"}})
```

## Model Management

### List Models

```clojure
; List all models
(pyjama.core/ollama url :tags)

; List model names only
(pyjama.core/ollama url :tags {} (fn [res] (map :name (res :models))))
; => ("llama3.2:1b" "llama3.2:latest" "llava:latest")
```

### Show Model Info

```clojure
; Show all models
(pyjama.core/ollama url :show)

; Show specific model
(pyjama.core/ollama url :show {:model "llama3.2"})

; Show model details only
(pyjama.core/ollama url :show {:model "llama3.2"} :details)
; => {:parent_model "", :format "gguf", :family "llama", ...}
```

### Pull Models

```clojure
; Pull model with streaming
(pyjama.core/ollama
  url
  :pull
  {:stream true}
  pyjama.core/print-pull-tokens)

; Pull model non-streaming
(pyjama.core/ollama
  url
  :pull {:model "llama3.2:1b"})
; => {:status "success"}
```

### Create Models

```clojure
; Create quantized model (non-streaming)
(pyjama.core/ollama
  url
  :create
  {
   :model     "llama3.2:quantized"
   :modelfile "FROM llama3.1:8b-instruct-fp16"
   :quantize  "q4_K_M"
   }
  identity)

; Create with streaming progress
(pyjama.core/ollama
  url
  :create
  {
   :model     "llama3.2:quantized"
   :modelfile "FROM llama3.1:8b-instruct-fp16"
   :quantize  "q8_0"
   :stream true
   }
  pyjama.core/print-create-tokens)
```

## Embeddings

Generate text embeddings for similarity search:

```clojure
; Single text embedding
(pyjama.core/ollama
  url
  :embed
  {:input "The sky is blue because the smurfs are too."})

; Multiple embeddings
(pyjama.core/ollama
  url
  :embed
  {:input [
           "The sky is blue because the smurfs are too."
           "The sky is red in the evening because the grand smurf is too."
           ]})
```

## Image Generation

Generate AI images using models like `alibaba/z-image-turbo`:

```clojure
; Basic image generation
(pyjama.core/ollama
  url
  :generate-image
  {:prompt "A serene mountain landscape at sunset"
   :model "alibaba/z-image-turbo"})

; With streaming progress
(pyjama.core/ollama
  url
  :generate-image
  {:prompt "A futuristic city with flying cars"
   :model "alibaba/z-image-turbo"
   :stream true})

; Custom dimensions and steps
(pyjama.core/ollama
  url
  :generate-image
  {:prompt "A portrait in the style of Van Gogh"
   :model "alibaba/z-image-turbo"
   :width 1024
   :height 1024
   :num-steps 20})
```

For full-stack image generation applications, see:
- [docs/OLLAMA_IMAGE_GENERATION.md](OLLAMA_IMAGE_GENERATION.md)
- [pyjama-agent-showcases/image-generator-agent](https://github.com/hellonico/pyjama-agent-showcases/tree/main/image-generator-agent)

## See Also

- [Agent Framework Documentation](LOOP_SUPPORT.md)
- [Ollama Image Generation](OLLAMA_IMAGE_GENERATION.md)
- [API Reference](../README.md)
