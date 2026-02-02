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
  "Main Gemini API function (non-streaming)
  
   Returns the complete response as a string."
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

(defn gemini-stream
  "Streaming Gemini API function
   
   Parameters:
   - config: Standard config map with :prompt, :model, etc.
   - on-chunk: Callback function called for each text chunk (fn [text] ...)
   - on-complete: Optional callback when stream completes (fn [] ...)
   
   Example:
   (gemini-stream {:prompt \"Tell me a story\"}
                  (fn [chunk] (print chunk) (flush))
                  (fn [] (println \"\\nDone!\")))"
  ([_config on-chunk]
   (gemini-stream _config on-chunk nil))
  ([_config on-chunk on-complete]
   (let [model (or (:model _config) "gemini-2.0-flash")
         config (utils/templated-prompt _config)
         key (secrets.core/require-secret!! :google-api-key)

         ;; Use streamGenerateContent endpoint  
         url (str gemini-endpoint "/" model ":streamGenerateContent?key=" key "&alt=sse")

         contents (build-messages (:prompt config) (:system config))

         body-map {:contents contents
                   :generationConfig {:temperature (or (:temperature config) 0.7)
                                      :maxOutputTokens (or (:max-tokens config) 2048)}}

         body (json/generate-string body-map)]

     (try
       (let [response (client/post url {:headers {"Content-Type" "application/json"}
                                        :body body
                                        :as :stream})
             stream (:body response)]

         ;; Read SSE stream line by line
         (with-open [reader (java.io.BufferedReader.
                             (java.io.InputStreamReader. stream "UTF-8"))]
           (loop [full-text ""]
             (if-let [line (.readLine reader)]
               ;; Process line and continue loop
               (let [new-text (if (and (seq line) (.startsWith line "data: "))
                                (let [json-str (subs line 6)  ; Remove "data: " prefix
                                      chunk (try
                                              (json/parse-string json-str true)
                                              (catch Exception _e nil))]
                                  (if chunk
                                    (if-let [text (-> chunk :candidates first :content :parts first :text)]
                                      (do
                                        (on-chunk text)
                                        text)
                                      "")
                                    ""))
                                "")]
                 (recur (str full-text new-text)))
               ;; No more lines - complete
               (do
                 (when on-complete (on-complete))
                 full-text)))))
       (catch Exception e
         (println "Error calling Gemini API (stream):" (.getMessage e))
         (throw e))))))
