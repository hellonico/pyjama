# Pyjama

A [Clojure](https://clojure.org/) client for [Ollama](https://ollama.com/)

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
```


## License

Copyright © 2024-2025 hellonico

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
