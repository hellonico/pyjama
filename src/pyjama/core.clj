(ns pyjama.core
  (:require
   ;; External dependencies
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.set :as set]

   ;; Internal dependencies
   [pyjama.deepseek.core]
   [pyjama.openrouter.core]
   [pyjama.chatgpt.core]
   [pyjama.claude.core]
   [pyjama.gemini.core]
   [pyjama.utils :as utils])

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

(defn print-generate-image-tokens
  "Print image generation progress or return image data"
  [parsed]
  (if (:done parsed)
    (:image parsed)
    (when-let [progress (some-> parsed (select-keys [:completed :total]))]
      (print (str "\rProgress: " (:completed progress) "/" (:total progress)))
      (flush))))

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
              :response]

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


   :generate-image [{:model      "x/z-image-turbo"
                     :keep_alive "5m"
                     :stream     false
                     :width      1024
                     :height     768}
                    :post
                    print-generate-image-tokens
                    :image]
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

(defn pipe-generate-image-progress
  "Pipe image generation progress to channel"
  [ch parsed]
  (when (or (:completed parsed) (:total parsed) (:done parsed))
    (async/go (async/>! ch parsed))))


;; =============================================================================
;; Utility Functions
;; =============================================================================

(defn stream
  "Process streaming response line by line, return final result from callback"
  [_fn body]
  (let [stream (io/reader body)]
    (reduce (fn [acc line]
              (let [parsed (json/parse-string line true)
                    result (_fn parsed)]
                ;; Return the result if non-nil, otherwise keep accumulator
                (or result acc)))
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
               :generate-image print-generate-image-tokens
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
                  :url         (str url "/api/" (if (= command :generate-image) "generate" (name command)))
                  :headers     {"Content-Type" "application/json"}
                  :body        body
                  :as          (if streaming? :stream :json)
                  :raw-stream? false
                  :async?      false}
         response (try (client/request options)
                       (catch Exception e
                         (println "Error while calling:" url " and model:" (:model params))
                         (throw e)))
         body (:body response)]
     (if streaming?
       ((partial stream _fn) body)
       (_fn body)))))

;; =============================================================================
;; Agent Registry and Configuration
;; =============================================================================

(defn- deep-merge
  "Recursively merges maps."
  [& maps]
  (if (every? map? maps)
    (apply merge-with deep-merge maps)
    (last maps)))

(defn normalize-agent-data
  "Normalize agent data to handle both single-agent and multi-agent formats.
  
  Single-agent format (has :steps at top level):
    {:description ... :steps {...}}
  
  Multi-agent format (map of agent-id -> spec):
    {:agent-1 {:description ... :steps {...}}
     :agent-2 {:description ... :steps {...}}}
  
  For single-agent format, wraps the spec with agent-name as key.
  Returns a map of agent-id -> agent-spec in both cases."
  [data agent-name]
  (if (contains? data :steps)
    ;; Single agent: wrap with agent name
    {(keyword agent-name) data}
    ;; Multi-agent: use as-is
    data))

(defn- load-edn-files-from-dir
  "Load all .edn files from a directory and merge them"
  [dir]
  (when (.exists dir)
    (let [edn-files (->> (.listFiles dir)
                         (filter #(and (.isFile %)
                                       (.endsWith (.getName %) ".edn")))
                         (sort-by #(.getName %)))]
      (when (seq edn-files)
        ;; Load each file and normalize it (handles both single and multi-agent formats)
        (let [normalized-configs (map (fn [file]
                                        (let [data (edn/read-string (slurp file))
                                              agent-name (str/replace (.getName file) #"\.edn$" "")]
                                          (normalize-agent-data data agent-name)))
                                      edn-files)]
          (apply deep-merge normalized-configs))))))

(defn load-agents
  "Load and merge agent configuration from (in order of precedence):
   1. agents.edn specified in system property (highest)
   2. agents.edn in the current working directory
   3. agents/ directory in the current working directory (modular agents)
   4. agents.edn found in the classpath
   5. agents/ directory in the classpath (modular agents, lowest)
   Returns a merged EDN map."
  []
  (let [prop-path (System/getProperty "agents.edn")

        ;; Handle system property (file, directory, or comma-separated)
        prop-configs (when prop-path
                       (let [paths (str/split prop-path #",")]
                         (keep (fn [p]
                                 (let [trimmed (str/trim p)
                                       file (io/file trimmed)]
                                   (cond
                                     ;; Directory: load all .edn files
                                     (.isDirectory file)
                                     (load-edn-files-from-dir file)

                                     ;; File: load single file
                                     (.exists file)
                                     (let [data (edn/read-string (slurp file))
                                           ;; Extract agent name from filename
                                           agent-name (str/replace (.getName file) #"\.edn$" "")]
                                       (normalize-agent-data data agent-name))

                                     ;; Not found - skip
                                     :else
                                     nil)))
                               paths)))

        cwd-file (io/file "agents.edn")
        cwd-dir (io/file "agents")
        cp-resource (io/resource "agents.edn")
        cp-dir-url (io/resource "agents/")
        cp-dir (when cp-dir-url
                 (try
                   (io/file (.toURI cp-dir-url))
                   (catch Exception _ nil)))

        configs (keep identity
                      (concat
                       ;; Classpath resources (lowest priority)
                       [(when cp-resource (edn/read-string (slurp cp-resource)))
                        (when cp-dir (load-edn-files-from-dir cp-dir))
                        (when (.exists cwd-file) (edn/read-string (slurp cwd-file)))
                        (when (.exists cwd-dir) (load-edn-files-from-dir cwd-dir))]
                       ;; System property configs (highest priority)
                       prop-configs))]    (apply deep-merge configs)))

(def agents-registry
  "Dynamic agents registry - initialized with loaded agents, can be updated at runtime"
  (atom (or (load-agents) {})))

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

(defmethod pyjama-call :gemini
  [params]
  (if (or (:stream params) (:streaming params) (:on-chunk params))
    ;; Streaming mode
    (pyjama.gemini.core/gemini-stream
     params
     (or (:on-chunk params) (fn [chunk] (print chunk) (flush)))
     (:on-complete params))
    ;; Non-streaming mode
    (pyjama.gemini.core/gemini params)))

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

;; =============================================================================
;; Agent Registry & Discovery
;; =============================================================================

(defn- extract-template-keys
  "Extract required keys from a template string like {{foo}}"
  [s]
  (when (string? s)
    (->> (re-seq #"\{\{([^}]+)\}\}" s)
         (map second)
         (map str/trim)
         (map keyword)
         set)))

(defn- extract-inputs
  "Analyze agent spec to determine required inputs"
  [spec]
  (let [args (:args spec)
        steps (:steps spec)
        ;; Extract from :args map keys and template values
        arg-keys (set (keys args))
        template-keys (reduce (fn [acc v]
                                (if (string? v)
                                  (into acc (extract-template-keys v))
                                  acc))
                              #{}
                              (vals args))
        ;; Also look for {{...}} in step arguments just in case
        step-template-keys (reduce (fn [acc step]
                                     (reduce (fn [inner-acc v]
                                               (if (string? v)
                                                 (into inner-acc (extract-template-keys v))
                                                 inner-acc))
                                             acc
                                             (vals (:args step))))
                                   #{}
                                   (vals steps))]
    (sort (into (into arg-keys template-keys) step-template-keys))))

(defn describe-agent
  "Return a metadata map for the given agent ID"
  [id]
  (when-let [spec (get @agents-registry id)]
    (if (map? spec)
      {:id id
       :description (:description spec "No description provided")
       :type (if (:steps spec) :graph :simple)
       :inputs (extract-inputs spec)
       :outputs [:result] ;; Default implicit output
       :spec spec}
      {:id id
       :description "Legacy sequence agent"
       :type :sequence
       :inputs []
       :outputs [:result]
       :spec spec})))

(defn list-agents
  "List all available agents with their metadata"
  []
  (->> (keys @agents-registry)
       (map describe-agent)
       (sort-by :id)))

(defn search-agents
  "Search agents by name or description"
  [query]
  (let [q (str/lower-case (or query ""))
        agents (list-agents)]
    (if (str/blank? q)
      agents
      (filter (fn [agent]
                (or (str/includes? (str/lower-case (name (:id agent))) q)
                    (str/includes? (str/lower-case (:description agent)) q)))
              agents))))

(defn run-agent
  "Run an agent by ID with provided inputs map"
  [id inputs]
  (let [agent (describe-agent id)]
    (when-not agent
      (throw (ex-info (str "Agent not found: " id) {:id id})))

    (let [required (set (:inputs agent))
          provided (set (keys inputs))
          missing (set/difference required provided)]
      ;; Warn but allow execution (some inputs might be optional contexts)
      (when (seq missing)
        (println "⚠️  Warning: Potential missing inputs for agent" id ":" missing)))

    (call (merge {:id id} inputs))))
