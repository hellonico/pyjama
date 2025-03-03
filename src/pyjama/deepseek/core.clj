(ns pyjama.deepseek.core
  (:require [cheshire.core :as json]
            [clj-http.client :as client]))


(defn handle-response
  "Handles the response from the DeepSeek API."
  [response]
  (let [status (:status response)
        body (:body response)]
    (if (= status 200)
      (let [parsed-body (json/parse-string body true)]
        (when-let [content (get-in parsed-body [:choices 0 :message :content])]
          (println "Success:" content)))
      (println "Error:" body))))

(def api-key
  (System/getenv "DEEPSEEK_API_KEY"))

(defn call
  [{:keys [model prompt]}]
  (let [url "https://api.deepseek.com/chat/completions"     ; Replace with actual endpoint
        headers {"Authorization" (str "Bearer " api-key)
                 "Content-Type"  "application/json"}
        body {:prompt      prompt
              :model       model
              :messages    [{:role "system" :content "You are a helpful assistant."},
                            {:role :user :content prompt}]
              :stream      false
              :temperature 0.7
              }]
    (handle-response
      (client/post url {:headers      headers
                        :body         (json/generate-string body)
                        :content-type :json
                        :accept       :json}))))