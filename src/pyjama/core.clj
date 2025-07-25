(ns pyjama.core
 (:require [cheshire.core :as json]
           [clj-http.client :as client]
           [clojure.core.async :as async]
           [clojure.edn :as edn]
           [clojure.java.io :as io]

           [pyjama.deepseek.core]
           [pyjama.openrouter.core]
           [pyjama.chatgpt.core]
           [pyjama.utils]

           )
 (:import [java.time LocalDateTime]
          [java.time.format DateTimeFormatter]))

(defn print-tokens [parsed key]
 (when-let [resp (get-in parsed key)]
  (flush)))

(defn print-create-tokens [parsed]
 (print-tokens parsed [:status]))

(defn print-pull-tokens [parsed]
 (print-tokens parsed [:status]))

(defn print-generate-tokens [parsed]
 (print-tokens parsed [:response]))

(defn print-chat-tokens [parsed]
 (print-tokens parsed [:message :content]))

(defn pipe-tokens [ch json-path parsed]
 (when-let [resp (get-in parsed json-path)]
  (async/go (async/>! ch resp))))

(defn pipe-generate-tokens [ch parsed]
 (pipe-tokens ch [:response] parsed))

(defn pipe-chat-tokens [ch parsed]
 (pipe-tokens ch [:message :content] parsed))

(defn pipe-pull-tokens [ch parsed]
 (pipe-tokens ch [:status] parsed))

(defn stream [_fn body]
 (let [stream (io/reader body)]
  (reduce (fn [_ line]
           (_fn (json/parse-string line true)))
          nil
          (line-seq stream))))

(defn keys-to-keywords [m]
 (into {} (map (fn [[k v]] [(keyword (name k)) v]) m)))

(defn structure-to-edn [body]
 (-> body :response json/parse-string keys-to-keywords))

(def DEFAULTS
 {
  ; api function name
  :generate
  [
   {:model      "llama3.2"
    :keep_alive "5m"
    :stream     false
    :images     []
    ;:system     "You are a very serious computer assistant. Only give brief answer."
    }
   :post
   print-generate-tokens
   ]

  :tags
  [{} :get identity]

  :show
  [{:model   "llama3.2"
    :verbose false}
   :post
   identity]

  :pull
  [{:model  "llama3.2"
    :stream false}
   :post
   identity]

  :ps
  [{}
   :get
   identity
   ]

  :create
  [{} :post identity]

  :delete
  [{}
   :delete
   identity]

  :chat
  [{:model      "llama3.2"
    :keep_alive "5m"
    :stream     false
    :messages   [{:role :user :content "why is the sky blue?"}]}
   :post
   print-chat-tokens]

  :embed
  [{:model "all-minilm"}
   :post
   :embeddings
   ]

  :version
  [{} :get identity]

  })

(defn ollama
 ([url command]
  (ollama url command {}))
 ([url command input]
  (assert ((into #{} (keys DEFAULTS)) command)
          (str "Invalid command: " command))
  (ollama url command input (-> command DEFAULTS last)))
 ([url command input _fn]
  (let [
        cmd-params (command DEFAULTS)
        params (merge (nth cmd-params 0) (pyjama.utils/templated-prompt input))
        streaming? (:stream params)
        body (json/generate-string params)
        options {:method      (nth cmd-params 1)
                 :url         (str url "/api/" (name command))
                 :headers     {"Content-Type" "application/json"}
                 :body        body
                 :as          (if streaming? :stream :json)
                 :raw-stream? false
                 :async?      false}
        response (client/request options)
        body (:body response)
        ]
   (if streaming?
    ((partial stream _fn) body)
    (_fn body)))))

;
; NEW DISPATCH and REGISTRY
;


;; 1. Load agent configuration from agent.edn
(defn load-agents []
 (let [prop-path (System/getProperty "agents.edn")          ;; Check system property
       prop-file (when prop-path (io/file prop-path))
       cwd-file (io/file "agents.edn")]                     ;; Fallback to current dir
  (cond
   (and prop-file (.exists prop-file))
   (edn/read-string (slurp prop-file))

   (.exists cwd-file)
   (edn/read-string (slurp cwd-file))

   :else
   {})))

(def agents-registry
 (delay (or (load-agents) {})))         ;; Lazy load


(def URL (or (System/getenv "OLLAMA_URL") "http://localhost:11434"))

;; Define the multimethod
(defmulti pyjama-call :impl)

(defmethod pyjama-call :chatgpt
 [params]
 (pyjama.chatgpt.core/chatgpt params))

;; Implementations
(defmethod pyjama-call :ollama
 [params]
 (ollama (or (:url params) URL) :generate params :response))

(defmethod pyjama-call :deepseek
 [params]
 (pyjama.deepseek.core/call params))

(defmethod pyjama-call :openrouter
 [params]
 (pyjama.openrouter.core/with-config params))

(defmethod pyjama-call :default
 [params]
 (throw (ex-info "Unknown pyjama-call implementation"
                 {:impl (:impl params)})))

;; Detect the current implementation from env or system properties
(defn current-impl []
 (let [prop (System/getProperty "PYJAMA_IMPL")
       env (System/getenv "PYJAMA_IMPL")]
  (keyword (or prop env "ollama"))))

;; 4. Merge agent defaults
(defn resolve-params [params]
 (if-let [agent-id (:id params)]
  (let [defaults (get @agents-registry agent-id)
        default-maps (or (get @agents-registry :default) {})
        resource-path (str "prompts/" (name agent-id) ".txt")
        prompt-map (if-let [_ (io/resource resource-path)]
                    {:pre resource-path}
                    {})
        ]
   (merge {:impl (or (:impl params) (current-impl))}
          default-maps
          defaults
          prompt-map
          params
          ))
  (merge params {:impl (or (:impl params) (current-impl))})))


;; 5. Create a simple timestamp
(defn now-str []
 (.format (LocalDateTime/now) (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss")))


(defn current-app-id []
 (let [cmd (System/getProperty "sun.java.command")
       tokens (clojure.string/split cmd #"\s+")
       idx (.indexOf tokens "-m")]
  (if (and (pos? idx) (< (inc idx) (count tokens)))
   (nth tokens (inc idx))
   (first tokens))))  ;; fallback to first token (main class/jar)

;; 6. Logging with :id
(defn log-call [params]
 (let [log-file (io/file (str (System/getProperty "user.home") "/pyjama.edn"))
       entry {:datetime (now-str)
              :id       (:id params)                        ;; include id if present
              :impl     (:impl params)
              :pre      (:pre params)
              :app-id   (current-app-id)
              :model    (:model params)}]
  (spit log-file (str entry "\n") :append true)))

;; 7. Public entry point
(defn call* [params]
 (let [resolved (resolve-params params)
       result   (pyjama-call (dissoc resolved :id))]
  (log-call resolved)
  (if (:format resolved)
   (pyjama.utils/parse-json-or-text result)
   result)))

(defn call [params]
 (let [entry (get @agents-registry (:id params))]
  (if (vector? entry)
   (reduce
    (fn [prev-output step-id]
     (call* (merge params  {:prompt prev-output :id step-id} )))
    params
    entry)
   (call* params))))