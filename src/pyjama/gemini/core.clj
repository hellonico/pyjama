(ns pyjama.gemini.core
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [secrets.core]
   [pyjama.utils :as utils]))

(def gemini-endpoint "https://generativelanguage.googleapis.com/v1beta/models")



(defn- build-messages [prompt system]
  (let [contents (cond-> []
                   (seq system) (conj {:role "user" :parts [{:text (str "System instruction: " system)}]})
                   (seq system) (conj {:role "model" :parts [{:text "Understood."}]})
                   prompt       (conj {:role "user" :parts [{:text prompt}]}))]
    contents))

(defn gemini
  "Main Gemini API function"
  [_config]
  (let [model (or (:model _config) "gemini-2.0-flash")
        config (utils/templated-prompt _config)
        key (secrets.core/require-secret!! :google-api-key)]

    (let [url (str gemini-endpoint "/" model ":generateContent?key=" key)

          contents (build-messages (:prompt config) (:system config))

          body-map {:contents contents
                    :generationConfig {:temperature (or (:temperature config) 0.7)
                                       :maxOutputTokens (or (:max-tokens config) 2048)}}

          body (json/generate-string body-map)]

      (try
        (let [response (client/post url {:headers {"Content-Type" "application/json"}
                                         :body body
                                         :as :json})
              parsed (:body response)
              text (-> parsed :candidates first :content :parts first :text)]
          text)
        (catch Exception e
          (println "Error calling Gemini API:" (.getMessage e))
          (println "Body:" body)
          (throw e))))))
