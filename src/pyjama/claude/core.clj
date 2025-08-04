(ns pyjama.claude.core
  (:require
   ;; External dependencies
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   
   ;; Internal dependencies
   [pyjama.image :refer [image-to-base64]]
   [pyjama.utils :as utils])
  
  (:import (java.io PrintWriter)))

;; =============================================================================
;; Configuration and Constants
;; =============================================================================

(def anthropic-endpoint "https://api.anthropic.com/v1")
(def url anthropic-endpoint)

(defn api-key
  "Get Anthropic API key from environment"
  []
  (System/getenv "ANTHROPIC_API_KEY"))

;; =============================================================================
;; Response Handling Functions
;; =============================================================================

(defn handle-streaming-response
  "Handle streaming response from Anthropic API with real-time output"
  [response]
  (binding [*out* (PrintWriter. System/out true)]
    (with-open [reader (io/reader (:body response))]
      (doseq [line (line-seq reader)]
        (when (and (seq line) (.startsWith ^String line "data: "))
          (let [json-str (subs line 6)]
            (when-not (= json-str "[DONE]")
              (try
                (let [parsed (json/parse-string json-str true)]
                  (when-let [content (get-in parsed [:delta :text])]
                    (print content)
                    (flush)))
                (catch Exception e
                  (println "\n[Error parsing chunk]:" (.getMessage e)))))))))))

(defn handle-error
  "Handle errors gracefully"
  [ex]
  (println "Error:" (.getMessage ex)))

;; =============================================================================
;; Message Building Functions
;; =============================================================================

(defn build-vision-message
  "Build message for Claude vision model with image support"
  [prompt image-base64]
  [{:role "user"
    :content [{:type "text" :text prompt}
              {:type "image"
               :source {:type "base64"
                        :media_type "image/jpeg"
                        :data image-base64}}]}])

(defn build-messages
  "Build messages for Claude API call"
  [config image-base64]
  (if image-base64
    (build-vision-message (:prompt config) image-base64)
    [{:role "user" :content (:prompt config)}]))

;; =============================================================================
;; Main API Functions
;; =============================================================================

(defn claude
  "Main Claude API function with support for streaming and vision"
  [_config]
  (let [url (str anthropic-endpoint "/messages")
        config (utils/templated-prompt _config)
        stream? (true? (or (:streaming config) (:stream config)))
        headers {"x-api-key" (api-key)
                 "Content-Type" "application/json"
                 "anthropic-version" "2023-06-01"}
        image-path (:image-path config)
        image-base64 (when image-path (image-to-base64 image-path))
        messages (build-messages config image-base64)
        body (json/generate-string
              {:model       (or (:model config) "claude-3-5-sonnet-20241022")
               :messages    messages
               :max_tokens  (or (:max_tokens config) 4096)
               :temperature (or (:temperature config) 0.7)
               :stream      stream?})
        response (client/post url {:headers headers 
                                  :body body 
                                  :as (if stream? :stream :json)})]
    
    (if stream?
      (handle-streaming-response response)
      (-> response :body :content first :text))))

(def call claude)

;; =============================================================================
;; Streaming Functions
;; =============================================================================

(defn stream-claude-response
  "Stream Claude response for given prompt"
  [prompt]
  (let [headers {"x-api-key" (api-key)
                 "Content-Type" "application/json"
                 "anthropic-version" "2023-06-01"}
        body (json/generate-string
              {:model       "claude-3-5-sonnet-20241022"
               :messages    [{:role "user" :content prompt}]
               :max_tokens  4096
               :temperature 0.7
               :stream      true})]
    
    (client/post url
                 {:headers headers
                  :body    body
                  :as      :stream
                  :async?  false}
                 handle-streaming-response
                 handle-error)))

;; =============================================================================
;; Structured Output Functions
;; =============================================================================

(defn get-structured-response
  "Get structured response from Claude using tools"
  [prompt schema]
  (let [headers {"x-api-key" (api-key)
                 "Content-Type" "application/json"
                 "anthropic-version" "2023-06-01"}
        body (json/generate-string
              {:model       "claude-3-5-sonnet-20241022"
               :messages    [{:role "user" :content prompt}]
               :max_tokens  4096
               :temperature 0.7
               :tools       [{:type "function"
                              :function {:name "extract_data"
                                         :description "Extract structured data from the response"
                                         :parameters schema}}]
               :tool_choice {:type "function"
                             :function {:name "extract_data"}}})]
    
    (-> (client/post url {:headers headers :body body :as :json})
        :body
        :content
        first
        :tool_use
        first
        :input
        (#(json/parse-string % (fn [k] (keyword k))))))) 