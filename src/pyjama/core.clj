(ns pyjama.core
  (:require
    ;; External dependencies
    [cheshire.core :as json]
    [clj-http.client :as client]
    [clojure.core.async :as async]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.java.io :as io]

    ;; Internal dependencies
    [pyjama.deepseek.core]
    [pyjama.openrouter.core]
    [pyjama.chatgpt.core]
    [pyjama.claude.core]
    [pyjama.utils :as utils]
    [pyjama.utils])

  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

;; =============================================================================
;; Token Printing Functions
;; =============================================================================

(defn print-tokens
  "Print tokens from parsed response at specified key path"
  [parsed key]
  (when-let [resp (get-in parsed key)]
    (print resp)
    (flush)))

(defn print-create-tokens
  "Print create operation tokens"
  [parsed]
  (print-tokens parsed [:status]))

(defn print-pull-tokens
  "Print pull operation tokens"
  [parsed]
  (print-tokens parsed [:status]))

(defn print-generate-tokens
  "Print generate operation tokens"
  [parsed]
  (print-tokens parsed [:response]))

(defn print-chat-tokens
  "Print chat operation tokens"
  [parsed]
  (print-tokens parsed [:message :content]))

;; =============================================================================
;; Constants and Configuration
;; =============================================================================

(def DEFAULTS
  "Default configuration for Ollama API calls"
  {:generate [{:model      "llama3.2"
               :keep_alive "5m"
               :stream     false
               :images     []}
              :post
              ;print-generate-tokens
              :response
              ]

   :tags     [{} :get identity]

   :show     [{:model   "llama3.2"
               :verbose false}
              :post
              identity]

   :pull     [{:model  "llama3.2"
               :stream false}
              :post
              identity]

   :ps       [{} :get identity]

   :create   [{} :post identity]

   :delete   [{} :delete identity]

   :chat     [{:model      "llama3.2"
               :keep_alive "5m"
               :stream     false
               :messages   [{:role :user :content "why is the sky blue?"}]}
              :post
              print-chat-tokens]

   :embed    [{:model "all-minilm"}
              :post
              :embeddings]

   :version  [{} :get identity]})

(def URL
  "Default Ollama URL from environment or localhost"
  (or (System/getenv "OLLAMA_URL") "http://localhost:11434"))

;; =============================================================================
;; Async Token Piping Functions
;; =============================================================================

(defn pipe-tokens
  "Pipe tokens from parsed response to channel at specified JSON path"
  [ch json-path parsed]
  (when-let [resp (get-in parsed json-path)]
    (async/go (async/>! ch resp))))

(defn pipe-generate-tokens
  "Pipe generate tokens to channel"
  [ch parsed]
  (pipe-tokens ch [:response] parsed))

(defn pipe-chat-tokens
  "Pipe chat tokens to channel"
  [ch parsed]
  (pipe-tokens ch [:message :content] parsed))

(defn pipe-pull-tokens
  "Pipe pull tokens to channel"
  [ch parsed]
  (pipe-tokens ch [:status] parsed))

;; =============================================================================
;; Utility Functions
;; =============================================================================

(defn stream
  "Process streaming response line by line"
  [_fn body]
  (let [stream (io/reader body)]
    (reduce (fn [_ line]
              (_fn (json/parse-string line true)))
            nil
            (line-seq stream))))

(defn keys-to-keywords
  "Convert map keys to keywords"
  [m]
  (into {} (map (fn [[k v]] [(keyword (name k)) v]) m)))

(defn structure-to-edn
  "Convert structured response to EDN format"
  [body]
  (-> body :response json/parse-string keys-to-keywords))

;; =============================================================================
;; Ollama API Functions
;; =============================================================================

(defn ollama
  "Main Ollama API function with multiple arities"
  ([url command]
   (ollama url command {}))

  ([url command input]
   (assert ((into #{} (keys DEFAULTS)) command)
           (str "Invalid command: " command))
   (ollama url command input
           (if (or (:streaming input) (:stream input))
             ; TODO: move to DEFAULTS
             (case command
               :generate print-generate-tokens
               :chat print-chat-tokens
               :create print-create-tokens
               :pull print-pull-tokens
               :response)
             (-> command DEFAULTS last))
           ;(if (= command :generate) :response identity)
           ))

  ([url command input _fn]
   (let [cmd-params (command DEFAULTS)
         params (merge (nth cmd-params 0) (pyjama.utils/templated-prompt input))
         streaming? (or (:stream params) (:streaming params))
         body (json/generate-string params)
         options {:method      (nth cmd-params 1)
                  :url         (str url "/api/" (name command))
                  :headers     {"Content-Type" "application/json"}
                  :body        body
                  :as          (if streaming? :stream :json)
                  :raw-stream? false
                  :async?      false}
         response (try (client/request options)
                       (catch Exception e
                         (println "Error while calling:" url " and model:" (:model params))
                         (throw e)
                         ))
         body (:body response)]
     (if streaming?
       ((partial stream _fn) body)
       (_fn body)))))

;; =============================================================================
;; Agent Registry and Configuration
;; =============================================================================

(defn load-agents
  "Load agent configuration from:
   1. agents.edn specified in system property
   2. agents.edn in the current working directory
   3. agents.edn found in the classpath
   Returns an EDN map or empty map if not found."
  []
  (let [prop-path (System/getProperty "agents.edn")
        prop-file (when prop-path (io/file prop-path))
        cwd-file (io/file "agents.edn")
        cp-resource (io/resource "agents.edn")]
    (cond
      (and prop-file (.exists prop-file))
      (edn/read-string (slurp prop-file))

      (.exists cwd-file)
      (edn/read-string (slurp cwd-file))

      cp-resource
      (edn/read-string (slurp cp-resource))

      :else
      {})))

(def agents-registry
  "Lazy-loaded agents registry"
  (delay (or (load-agents) {})))

;; =============================================================================
;; Implementation Dispatch
;; =============================================================================

(defmulti pyjama-call
          "Multimethod for different Pyjama implementations"
          :impl)

(defmethod pyjama-call :chatgpt
  [params]
  (pyjama.chatgpt.core/chatgpt params))

(defmethod pyjama-call :ollama
  [params]
  (ollama (or (:url params) URL) :generate params))

(defmethod pyjama-call :deepseek
  [params]
  (pyjama.deepseek.core/call params))

(defmethod pyjama-call :openrouter
  [params]
  (pyjama.openrouter.core/with-config params))

(defmethod pyjama-call :claude
  [params]
  (pyjama.claude.core/claude params))

(defmethod pyjama-call :default
  [params]
  (throw (ex-info "Unknown pyjama-call implementation"
                  {:impl (:impl params)})))

(defn current-impl
  "Detect current implementation from environment or system properties"
  []
  (let [prop (System/getProperty "PYJAMA_IMPL")
        env (System/getenv "PYJAMA_IMPL")]
    (keyword (or prop env "ollama"))))

;; =============================================================================
;; Parameter Resolution and Logging
;; =============================================================================

(defn resolve-params
  "Resolve and merge parameters with agent defaults"
  [params]
  (let [default-maps (or (get @agents-registry :default) {})]
    (if-let [agent-id (:id params)]
      (let [defaults (get @agents-registry agent-id)
            resource-path (str "prompts/" (name agent-id) ".txt")
            prompt-map (if-let [_ (io/resource resource-path)]
                         {:pre resource-path}
                         {})]
        (merge {:impl (current-impl)}
               default-maps
               defaults
               prompt-map
               params))
      (merge {:impl (or (:impl params) (current-impl))} default-maps params))))

(defn now-str
  "Create timestamp string in ISO format with date"
  []
  (.format (LocalDateTime/now) (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss")))

(defn current-app-id
  "Get current application ID from system properties"
  []
  (if-let [cmd (System/getProperty "sun.java.command")]
    (let [tokens (str/split cmd #"\s+")
          idx (.indexOf tokens "-m")]
      (if (and (pos? idx) (< (inc idx) (count tokens)))
        (nth tokens (inc idx))
        (first tokens)))
    "unknown"))

(defn log-call
  "Log API call to pyjama.edn file"
  [params]
  (let [log-file (io/file (str (System/getProperty "user.home") "/pyjama.edn"))
        entry (merge params {:datetime (now-str) :app-id (current-app-id)})]
    (spit log-file (str entry "\n") :append true)))

;; =============================================================================
;; Public API
;; =============================================================================

(defn call*
  "Internal call function with parameter resolution and logging"
  [params]
  (let [resolved (resolve-params params)
        ;_ (println resolved)
        result (pyjama-call (dissoc resolved :id))]
    (log-call resolved)
    (if (:format resolved)
      (pyjama.utils/parse-json-or-text result)
      result)))

(defn call
  "Main public API for Pyjama calls with agent support"
  [params]
  (let [entry (get @agents-registry (:id params))]
    (if (vector? entry)
      (reduce
        (fn [prev-output step-id]
          (call* (merge params {:prompt prev-output :id step-id})))
        (:prompt (utils/templated-prompt params))
        entry)
      (call* params))))