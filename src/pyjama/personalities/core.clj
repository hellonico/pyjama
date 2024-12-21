(ns pyjama.personalities.core
  (:require [pyjama.core]))

(defn make-personality
  [pconfig]
   (fn [_config & realtime]
     (let [
           config (if (map? _config) (merge _config pconfig) pconfig)
           input (if (map? _config) (:input _config) _config)
           ]
     (pyjama.core/ollama
       (or (:url config) (System/getenv "OLLAMA_URL") "http://localhost:11434")
       :generate
       {
        :model  (or (:model config) (System/getenv "PYJAMA_MODEL")  "llama3.2")
        :system (:system config)
        :prompt
        (format
          (:template config)
          input)
        }
       (if (:realtime config) pyjama.core/print-generate-tokens :response)))))

(def japanese-translator
  (make-personality
    {:system "Translate each sentence from Japanese to English with no explanation and no other text than the translation. "
     :template "%s"}))

(def samuraizer
  (make-personality
    {:system "Summarize each text you are getting, with no explanation and no other text than the summary.  "
     :template "%s"}))