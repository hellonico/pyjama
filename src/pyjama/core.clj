(ns pyjama.core
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.core.async :as async]
            [clojure.java.io :as io]))

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

(defn templated-prompt [input]
  (if (contains? input :pre)
    (let [pre (:pre input)
          prompt (:prompt input)
          template (if (vector? pre) (first pre) pre)
          args (concat (if (vector? pre) (rest pre) [])
                       (if (vector? prompt) prompt [prompt]))]
      (merge (dissoc input :pre) {:prompt (apply format template args)}))
    input))

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
         params (merge (nth cmd-params 0) (templated-prompt input))
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
