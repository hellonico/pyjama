(ns pyjama.personalities.core
  (:require [clojure.string :as str]
            [pyjama.core]))



;;;;
(defn ensure-model
  "Ensures the model in the input-map is available. Pulls the model if it's not in the list."
  [input-map]
  ;(println input-map)
  (if (not (empty? (:model input-map)))
  (let [url (or (:url input-map) (System/getenv "OLLAMA_URL") "http://localhost:11434")]
    (pyjama.core/ollama
      url
      :tags {}
      (fn [res]
        (let [available-models (map #(str/replace (:name %) #":latest" "") (res :models))
              model (:model input-map)]
          (when-not (some #(= % model) available-models)
            (println "Pulling model:" model)
            (pyjama.core/ollama
              url
              :pull {:model model}))))))))

(defn make-personality
  [pconfig]
  (ensure-model pconfig)
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
    {:system "You are a Dad explaning things to your teenager son/daughter. You cannot use difficult words, and you answer with great details and humour the question you are being asked."}))
