# Pyjama

A [Clojure](https://clojure.org/) client for [Ollama](https://ollama.com/)

Related blog [posts](http://blog.hellonico.info/tags/pyjama/)

## Usage

```clojure
; start the repl
; clj
(require '[pyjama.core])
(def url "http://localhost:11434")

; simple generate
(pyjama.core/ollama 
  url
  :generate 
  {:prompt "What color is the sky at night and why?"})

; generate with streaming
(pyjama.core/ollama
  url
  :generate
  {:prompt "What color is the sky at night and why?" :stream true})

; generate using json
(pyjama.core/ollama 
  url 
  :generate
    {
     :model "llama3.2"
     :prompt "What color is the sky at different times of the day? Respond using JSON"
     :format "json"
     :stream false
     })
; {"daytime": "blue", "sunset": "orange-red", "dusk": "pink-orange", "night": "dark blue or black"}

; structured output in json
(pyjama.core/ollama URL :generate
                    {:stream false :format structure
                     :model model
                     :prompt "Pyjama is 22 days old and is busy saving the world."}
                    :response)
; {"age": 22, "available": true}

; structured output in edn
(pyjama.core/ollama URL :generate
                    {:stream false
                     :format structure
                     :model model
                     :prompt "Pyjama is 22 days old and is busy saving the world."}
                    pyjama.core/structure-to-edn)
; {:age 22, :available true}

; What is in the picture?
(pyjama.core/ollama 
  url 
  :generate
  {:model "llava"
   :prompt "what is in this picture?"
    :stream true
    :images [(pyjama.image/image-to-base64 "resources/cute_cat.jpg")]})

; Reproducible Output
(pyjama.core/ollama 
  url 
  :generate
 {:model "llama3.2"
  :prompt "Why is the sky blue?" 
  :options {:seed 123}})

; load a model (if it exist)
(pyjama.core/ollama 
  url 
  :generate 
  {:model "qwq"} 
  pyjama.core/response)

; show all
(pyjama.core/ollama url :show)

; show all info on model
(pyjama.core/ollama url :show {:model "llama3.2"})

; show model details
(pyjama.core/ollama url :show {:model "llama3.2"} :details)
; {:parent_model "", :format "gguf", :family "llama", :families ["llama"], :parameter_size "3.2B", :quantization_level "Q4_K_M"}

; chat
(pyjama.core/ollama
  url
  :chat
  {:model "llama3.2" 
   :stream true 
   :message 
   {:role :user :content "Who is mario?"}})

; list models
(pyjama.core/ollama url :tags)

; list model names
(pyjama.core/ollama url :tags {} (fn [res] (map :name (res :models))))
; ("llama3.2:1b" "llama3.2:latest" "llava:latest" "sailor2:latest" "exaone3.5:2.4b")

; pull model streaming
(pyjama.core/ollama
  url
  :pull
  {:stream true}
  pyjama.core/print-pull-tokens)

; pull mondel non streaming
(pyjama.core/ollama
  url
  :pull {:model "llama3.2:1b"})
; {:status "success"}

; create non-streaming
(pyjama.core/ollama
  url
  :create
  {
   :model     "llama3.2:quantized"
   :modelfile "FROM llama3.1:8b-instruct-fp16"
   :quantize  "q4_K_M"
   }
  identity)

; create model streaming
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

; generate embeddings
(pyjama.core/ollama
  url
  :embed
  {:input "The sky is blue because the smurfs are too."})

; generate embeddings multiple outputs
(pyjama.core/ollama
  url
  :embed
  {:input [
           "The sky is blue because the smurfs are too."
           "The sky is red in the evening because the grand smurf is too."
           ]})

; generate image
(pyjama.core/ollama
  url
  :generate-image
  {:prompt "A serene mountain landscape at sunset"
   :model "alibaba/z-image-turbo"})

; generate image with streaming progress
(pyjama.core/ollama
  url
  :generate-image
  {:prompt "A futuristic city with flying cars"
   :model "alibaba/z-image-turbo"
   :stream true})

; generate image with custom options
(pyjama.core/ollama
  url
  :generate-image
  {:prompt "A portrait in the style of Van Gogh"
   :model "alibaba/z-image-turbo"
   :width 1024
   :height 1024
   :num-steps 20})
```

## Image Generation

Pyjama supports AI image generation through Ollama using models like `alibaba/z-image-turbo`. The generated images are returned as Base64-encoded data and can include real-time progress updates.

**Key Features:**
- Streaming progress updates during generation
- Customizable dimensions and generation steps
- State management for tracking generation progress
- Full integration with Ollama's image generation API

For detailed examples and full-stack implementations, see:
- [docs/OLLAMA_IMAGE_GENERATION.md](docs/OLLAMA_IMAGE_GENERATION.md) - Comprehensive documentation
- [pyjama-agent-showcases/image-generator-agent](https://github.com/hellonico/pyjama-agent-showcases/tree/main/image-generator-agent) - Full web application example

## Agent Framework

Pyjama includes a powerful agent framework for building autonomous, multi-step workflows using declarative EDN configuration.

### Loop Support (NEW!)

Pyjama now supports declarative loops for batch processing, eliminating the need for manual "Fetch-Pop-Loop" patterns:

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
- `{{loop-item}}` - Current item being processed
- `{{loop-index}}` - Zero-based index (0, 1, 2, ...)
- `{{loop-count}}` - Total number of items
- `{{loop-remaining}}` - Items remaining (including current)

**Benefits:**
- ✅ Simpler EDN configuration
- ✅ No manual routing or pop tools needed
- ✅ Automatic iteration management
- ✅ Built-in loop context variables
- ✅ Full trace support for each iteration

For detailed documentation and examples, see:
- [docs/LOOP_SUPPORT.md](docs/LOOP_SUPPORT.md) - Complete loop documentation
- [examples/loop-demo-agents.edn](examples/loop-demo-agents.edn) - Working examples

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
