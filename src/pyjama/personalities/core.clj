(ns pyjama.personalities.core
  (:require [pyjama.core]))

(defn make-personality
  [pconfig]
   (fn [_config & realtime]
     (let [
           config (if (map? _config) (merge _config pconfig) pconfig)
           realtime (true? (:stream config))
           ]
     (pyjama.core/ollama
       (or (:url config) (System/getenv "OLLAMA_URL") "http://localhost:11434")
       :generate
       (dissoc config :url)
       (if realtime pyjama.core/print-generate-tokens :response)))))

(def japanese-translator
  (make-personality
    {:system "Translate each sentence from Japanese to English with no explanation and no other text than the translation. "}))

(def samuraizer
  (make-personality
    {:system "Summarize each text you are getting, with no explanation and no other text than the summary, using Samurai language"}))

(def three-points
  (make-personality
    {:system "Summarize the whole text you are getting in list of three points maximum, YOU CAN NOT WRITE MORE THAN THREE SENTENCES IN YOUR WHOLE ANSWER, with no explanation and no other text than the summary."}))

(def dad
  (make-personality
    {:system "You are a Dad explaning things to your teenager son/daughter. You cannot use difficult words, and you answer with great details and humour the question you are being asked."  }))
