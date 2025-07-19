(ns pyjama.chatgpt.core
 (:require [cheshire.core :as json]
           [clj-http.client :as client]
           [clojure.java.io :as io]
           [pyjama.utils]))

(def api-key
 (System/getenv "OPENAI_API_KEY"))

(def openai-endpoint "https://api.openai.com/v1/chat/completions")
(def url openai-endpoint)

(defn chatgpt [_config]
 (let [
       url (or (:url _config) openai-endpoint)
       config (pyjama.utils/templated-prompt _config)
       headers {"Authorization" (str "Bearer " api-key)
                "Content-Type"  "application/json"}
       body (json/generate-string
             {:stream      (or (:streaming config) false)
              :model       (or (:model config) "gpt-4o")
              :messages    [{:role "system" :content (or (:system config) "You are a helpful assistant.")}
                            {:role "user" :content (:prompt config)}]
              :temperature (or (:temperature config) 0.7)})
       response (client/post url {:headers headers :body body :as :json})]
  (-> response :body :choices first :message :content)))
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


(defn handle-response [response]
 "Reads the streaming response and prints tokens as they arrive."
 (with-open [reader (io/reader (:body response))]
  (println "ok")
  (doseq [line (line-seq reader)]
   (when (seq line)
    (let [parsed (json/parse-string line true)]
     (when-let [content (get-in parsed [:choices 0 :delta :content])]
      (print content)
      (flush)))))))

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
