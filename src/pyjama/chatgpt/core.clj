(ns pyjama.chatgpt.core
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

(def openai-endpoint "https://api.openai.com/v1")
(def url openai-endpoint)

(defn api-key
  "Get OpenAI API key from environment"
  []
  (System/getenv "OPENAI_API_KEY"))

;; =============================================================================
;; Response Handling Functions
;; =============================================================================

(defn handle-response
  "Handle streaming response from OpenAI API with real-time output"
  [response]
  (binding [*out* (PrintWriter. System/out true)]
    (with-open [reader (io/reader (:body response))]
      (doseq [line (line-seq reader)]
        (when (and (seq line) (.startsWith ^String line "data: "))
          (let [json-str (subs line 6)]
            (when-not (= json-str "[DONE]")
              (try
                (let [parsed (json/parse-string json-str true)]
                  (let [content (get-in parsed [:choices 0 :delta :content])]
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

;; =============================================================================
;; Helpers
;; =============================================================================

(defn ^:private image->data-url
 "Return a data URL for an image file path. Defaults to image/jpeg."
 [path]
 (let [b64 (image-to-base64 path)
       mime (cond
             (re-find #"\.(?i)png$"  path) "image/png"
             (re-find #"\.(?i)webp$" path) "image/webp"
             (re-find #"\.(?i)gif$"  path) "image/gif"
             :else "image/jpeg")]
  (str "data:" mime ";base64," b64)))

(defn build-vision-message
 "Build message for vision model with 1+ images (data URLs or HTTP URLs).
  `images` should be a seq of strings: either data: URLs or http(s) URLs.
  `detail` is \"auto\"|\"low\"|\"high\"."
 [prompt images detail]
 (let [image-contents (for [url images]
                       {:type "image_url"
                        :image_url {:url url
                                    :detail (or detail "auto")}})]
  [{:role "user"
    :content (into [{:type "text" :text prompt}] image-contents)}]))

;; =============================================================================
;; Main API Function
;; =============================================================================

(defn chatgpt
 "Main ChatGPT API function with support for streaming and vision (multi-image)."
 [_config]
 (let [url (str (get _config :url "https://api.openai.com/v1") "/chat/completions")
       config   (utils/templated-prompt _config)
       stream?  (true? (or (:streaming config) (:stream config)))
       headers  {"Authorization" (str "Bearer " (api-key))
                 "Content-Type"  "application/json"}

       ;; --- Collect images (paths or URLs), staying backward compatible ---
       ;; Single local file path (legacy):
       legacy-path (:image-path config)
       ;; Multiple local file paths:
       paths       (seq (:image-paths config))
       ;; Already-hosted HTTP/HTTPS URLs:
       url-images  (seq (:image-urls config))
       ;; Optional detail hint for vision:
       detail      (:detail config) ; "auto" | "low" | "high"

       ;; Normalize everything to a seq of URLs (prefer data: URLs for local files)
       data-urls
       (cond
        (and legacy-path (not paths))
        [(image->data-url legacy-path)]

        paths
        (mapv image->data-url paths)

        :else
        nil)

       images (when (or data-urls url-images)
               (vec (concat (or data-urls []) (or url-images []))))

       messages (if (seq images)
                 (build-vision-message (:prompt config) images detail)
                 [{:role "system" :content (or (:system config) "You are a helpful assistant.")}
                  {:role "user"   :content (:prompt config)}])

       ;; Build request body conditionally
       body (json/generate-string
             (cond-> {:stream   stream?
                      :model    (or (:model config) "gpt-5-mini")
                      :messages messages}
                     (contains? config :temperature)
                     (assoc :temperature (:temperature config))))
       response (client/post url {:headers headers
                                  :body body
                                  :as (if stream? :stream :json)})]
  (if stream?
   (handle-response response)
   (-> response :body :choices first :message :content))))


(def call chatgpt)

;; =============================================================================
;; Streaming Functions
;; =============================================================================

(defn stream-chatgpt-response
  "Stream ChatGPT response for given prompt"
  [prompt]
  (let [headers {"Authorization" (str "Bearer " (api-key))
                 "Content-Type"  "application/json"}
        body (json/generate-string
              {:model    "gpt-4o"
               :messages [{:role "system" :content "You are a helpful assistant."}
                          {:role "user" :content prompt}]
               :stream   true})]
    
    (client/post url
                 {:headers headers
                  :body    body
                  :as      :stream
                  :async?  false}
                 handle-response
                 handle-error)))

;; =============================================================================
;; Structured Output Functions
;; =============================================================================

(def schema
  "JSON schema for structured story generation"
  {:type     "object"
   :properties
   {"title"   {:type "string", :description "The title of the joke"}
    "context" {:type "array", :items {:type "string"}, :description "surrounding story"}
    "joke"    {:type "string", :description "the joke"}}
   :required ["title" "context" "joke"]})

(defn get-json-response
  "Get structured JSON response from ChatGPT"
  [prompt]
  (let [headers {"Authorization" (str "Bearer " (api-key))
                 "Content-Type"  "application/json"}
        body (json/generate-string
              {:model           "gpt-4o"
               :messages        [{:role "system" :content "You are a story generator that outputs structured JSON."}
                                {:role "user" :content prompt}]
               :response_format {:type "json_object"}
               :tool_choice     {:type "function", :function {:name "generate_story"}}
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
