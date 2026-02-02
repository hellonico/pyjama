(ns pyjama.deepseek.core
  (:require [cheshire.core :as json]
            [pyjama.utils :as utils]
            [secrets.core]
            [clj-http.client :as client]))

(defn handle-response
  "Handles the response from the DeepSeek API."
  [response]
  (let [status (:status response)
        body (:body response)]
    (if (= status 200)
      (let [parsed-body (json/parse-string body true)]
        (when-let [content (get-in parsed-body [:choices 0 :message :content])]
          ;(println "Success:" content)
          content))
      (println "Error:" body))))

(defn api-key []
  (secrets.core/require-secret!! :deepseek-api-key))

(defn call
  [_config]
  (let [url "https://api.deepseek.com/chat/completions"     ; Replace with actual endpoint
        headers {"Authorization" (str "Bearer " (api-key))
                 "Content-Type"  "application/json"}
        config (utils/templated-prompt _config)
        {:keys [model prompt stream system]} config
        body {;:prompt      prompt
              :model       model
              :messages    [{:role "system" :content (or system "You are a helpful assistant.")},
                            {:role :user :content prompt}]
              :stream      stream
              :temperature 0.7}]
    (handle-response
     (client/post url {:headers      headers
                       :body         (json/generate-string body)
                       :content-type :json
                       :accept       :json}))))