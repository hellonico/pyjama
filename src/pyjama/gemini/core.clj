(ns pyjama.gemini.core
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as str]
   [secrets.core]
   [pyjama.utils :as utils]))

(def gemini-endpoint "https://generativelanguage.googleapis.com/v1beta/models")

(defn api-key
  "Get Google API key from secrets"
  []
  (or (secrets.core/get-secret :google-api-key)
      (System/getenv "GOOGLE_API_KEY")))

(defn- build-messages [prompt system]
  (let [contents (cond-> []
                   (seq system) (conj {:role "user" :parts [{:text (str "System instruction: " system)}]})
                   (seq system) (conj {:role "model" :parts [{:text "Understood."}]})
                   prompt       (conj {:role "user" :parts [{:text prompt}]}))]
    contents))

(defn gemini
  "Main Gemini API function"
  [_config]
  (let [model (or (:model _config) "gemini-1.5-flash")
        config (utils/templated-prompt _config)
        key (api-key)]

    (when (str/blank? key)
      (println "⚠️  Google API Key not found!")
      (println "   Please add :google-api-key to your secrets.edn")
      (println "   OR set GOOGLE_API_KEY environment variable."))

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
