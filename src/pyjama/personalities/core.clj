(ns pyjama.personalities.core
  (:require [pyjama.core]))

(defn make-personality
  ([config]
   (fn [input]
     (pyjama.core/ollama
       (or (:url config) (System/getenv "OLLAMA_URL") "http://localhost:11434")
       :generate
       {
        :model  (or (:model config) (System/getenv "PYJAMA_MODEL")  "llama3.2")
        :system (:system config)
        :prompt
        (format
          (:template config)
          input)}
       (if (not (or (:realtime config) true)) :response pyjama.core/print-generate-tokens)))))

(def japanese-translator
  (make-personality
    {:system "Translate each sentence from Japanese to English with no explanation and no other text than the translation. "
     :realtime false
     :template "%s"}))

(def samuraizer
  (make-personality
    {:system "Summarize each text you are getting, with no explanation and no other text than the summary.  "
     :realtime false
     :template "%s"}))