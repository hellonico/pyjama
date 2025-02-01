(ns pyjama.personalities
  (:require [clojure.string :as str]
            [pyjama.models :as m]
            [pyjama.core]))

(defn make-personality
  [pconfig]
  (m/ensure-model pconfig)
  (fn
    [config_or_prompt & realtime]
    (let [config
          (merge pconfig
                 (if (map? config_or_prompt)
                   config_or_prompt
                   {:prompt (if (not (empty? config_or_prompt))
                              config_or_prompt
                              (or (:prompt pconfig "")))}
                   )
                 )
          realtime (true? (:stream config))]
      (pyjama.core/ollama
        (or (:url config) (System/getenv "OLLAMA_URL") "http://localhost:11434")
        :generate
        (dissoc config :url)
        (cond realtime
              pyjama.core/print-generate-tokens
              (contains? config :format)
              (fn[res] (cheshire.core/parse-string (:response res) true))
              :else :response)))))


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
    {:system "You are a Dad explaning things to your teenager son/daughter. You cannot use difficult words, and you answer with great details and humour the question you are being asked."}))
