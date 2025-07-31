(ns pyjama.chatgpt.core
 (:require [cheshire.core :as json]
           [clj-http.client :as client]
           [clojure.java.io :as io]
           [pyjama.image :refer [image-to-base64]]
           [pyjama.utils :as utils])
 (:import (java.io PrintWriter)))

(def api-key
 (System/getenv "OPENAI_API_KEY"))

(def openai-endpoint "https://api.openai.com/v1")
(def url openai-endpoint)

;(defn handle-response [response]
; (with-open [reader (io/reader (:body response))]
;  (doseq [line (line-seq reader)]
;   (println ">> RAW LINE:" line))))

(defn handle-response [response]
 (binding [*out* (PrintWriter. System/out true)] ; auto-flush to terminal
  (with-open [reader (io/reader (:body response))]
   (doseq [line (line-seq reader)]
    (when (and (seq line) (.startsWith line "data: "))
     (let [json-str (subs line 6)]
      (when-not (= json-str "[DONE]")
       (try
        (let [parsed (json/parse-string json-str true)]
         (let [content (get-in parsed [:choices 0 :delta :content])]
          (print content)
          (flush)))
        (catch Exception e
         (println "\n[Error parsing chunk]:" (.getMessage e)))))))))))

(defn build-vision-message [prompt image-base64]
 [{:role "user"
   :content [{:type "text" :text prompt}
             {:type "image_url"
              :image_url {:url (str "data:image/jpeg;base64," image-base64)
                          :detail "auto"}}]}])


(defn chatgpt [_config]
 (let [url     "https://api.openai.com/v1/chat/completions"
       config  (utils/templated-prompt _config)
       stream? (true? (:streaming config))
       headers {"Authorization" (str "Bearer " api-key)
                "Content-Type"  "application/json"}
       image-path (:image-path config)
       image-base64 (when image-path (pyjama.image/image-to-base64 image-path))
       messages (if image-base64
                 (build-vision-message (:prompt config) image-base64)
                 [{:role "system" :content (or (:system config) "You are a helpful assistant.")}
                  {:role "user" :content (:prompt config)}])
       body    (json/generate-string
                {:stream      stream?
                 :model       (or (:model config) "gpt-4o")
                 :messages    messages
                 :temperature (or (:temperature config) 0.7)})
       response (client/post url {:headers headers :body body :as (if stream? :stream :json)})]

  (if stream?
   (handle-response response)
   (-> response :body :choices first :message :content))))





;(defn chatgpt [_config]
; (let [
;       url (str (or (:url _config) openai-endpoint) "/chat/completions")
;       config (pyjama.utils/templated-prompt _config)
;       headers {"Authorization" (str "Bearer " api-key)
;                "Content-Type"  "application/json"}
;       body (json/generate-string
;             {:stream      (or (:streaming config) false)
;              :model       (or (:model config) "gpt-4o")
;              :messages    [{:role "system" :content (or (:system config) "You are a helpful assistant.")}
;                            {:role "user" :content (:prompt config)}]
;              :temperature (or (:temperature config) 0.7)})
;       response (client/post url {:headers headers :body body :as :json})]
;  (-> response :body :choices first :message :content)))
(def call chatgpt)
;
;(defn handle-response [response]
;  "Reads and processes the SSE stream from OpenAI with real-time flushing."
;  (binding [*out* (PrintWriter. System/out true)]           ;; Disable buffering
;    (with-open [reader (io/reader (:body response))]
;      (doall (doseq [line (line-seq reader)]
;               (println line)
;               ;(flush)
;               (when (and (seq line) (.startsWith line "data: "))
;                 (let [json-str (subs line 6)]              ;; Remove "data: "
;                   (when-not (= json-str "[DONE]")          ;; Ignore the [DONE] marker
;                     (let [parsed (json/parse-string json-str true)]
;                       (flush)
;                       (when-let [content (get-in parsed [:choices 0 :delta :content])]
;                         (print content)                    ;; Print immediately
;                         ;(flush)))))))))))                  ;; Ensure real-time output
;;; Ensure real-time display

;
;(defn handle-response [response]
; "Reads the streaming response and prints tokens as they arrive."
; (with-open [reader (io/reader (:body response))]
;  (println "ok")
;  (doseq [line (line-seq reader)]
;   (when (seq line)
;    (let [parsed (json/parse-string line true)]
;     (when-let [content (get-in parsed [:choices 0 :delta :content])]
;      (print content)
;      (flush)))))))

(defn handle-error [ex]
 "Handles errors gracefully."
 (println "Error:" (.getMessage ex)))

(defn stream-chatgpt-response [prompt]
 (let [headers {"Authorization" (str "Bearer " api-key)
                "Content-Type"  "application/json"}
       ;url (or (:url _config) openai-endpoint)
       body (json/generate-string
             {:model       "gpt-4o"
              :messages    [{:role "system" :content "You are a helpful assistant."}
                            {:role "user" :content prompt}]
              ;:temperature 0.7
              :stream      true})]

  ;; Send request with streaming enabled
  (client/post url
               {:headers headers
                :body    body
                :as      :stream
                :async?  false}
               handle-response
               handle-error)))

(def schema
 {:type     "object"
  :properties
  {"title"   {:type "string", :description "The title of the joke"}
   "context" {:type "array", :items {:type "string"}, :description "surrounding story"}
   "joke"    {:type "string", :description "the joke"}}
  :required ["title" "context" "joke"]})

(defn get-json-response [prompt]
 (let [headers {"Authorization" (str "Bearer " api-key)
                "Content-Type"  "application/json"}
       body (json/generate-string
             {:model           "gpt-4o"
              :messages        [{:role "system" :content "You are a story generator that outputs structured JSON."}
                                {:role "user" :content prompt}]
              :response_format {:type "json_object"}
              :tool_choice     {:type "function", :function {:name "generate_story"}} ;; Force structured output
              :tools           [{:type     "function"
                                 :function {:name       "generate_story"
                                            :parameters schema}}]})]

  (-> (client/post url {:headers headers :body body :as :json})
      :body
      :choices
      first
      :message
      :tool_calls
      first
      :function
      :arguments
      (#(json/parse-string % (fn [k] (keyword k)))))))
