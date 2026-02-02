(ns pyjama.openrouter.core
  (:require [cheshire.core :as json]
            [secrets.core]
            [clj-http.client :as client]
            [clojure.pprint]))

(defn get-api-key []
  (secrets.core/require-secret!! :openrouter-api-key))

(defn with-config [config]
  (let [url "https://openrouter.ai/api/v1/chat/completions"
        headers {"Authorization" (str "Bearer " (get-api-key))
                 "Content-Type"  "application/json"}
        body {:model    (or (:model config) "anthropic/claude-3.7-sonnet")
              :messages [{:role "user" :content (:prompt config)}]}]
    (->
     (client/post
      url
      {:headers      headers
       :body         (json/generate-string body)
       :content-type :json
       :accept       :json})
     :body
     (json/parse-string true))))

(defn with-prompt
  [prompt]
  (with-config {:prompt prompt}))