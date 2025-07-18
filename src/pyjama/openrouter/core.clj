(ns pyjama.openrouter.core
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.pprint]))

(def open-router-api-key
  (System/getenv "OPENROUTER_API_KEY"))

(defn with-config [config]
  (let [url "https://openrouter.ai/api/v1/chat/completions"
        headers {"Authorization" (str "Bearer " open-router-api-key)
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