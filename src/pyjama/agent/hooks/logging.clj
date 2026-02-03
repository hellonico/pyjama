(ns pyjama.agent.hooks.logging
  "Logging hooks for agent tool execution.
  
  Provides automatic logging of tool executions with configurable
  verbosity levels and output formats."
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.time Instant ZonedDateTime ZoneId]
           [java.time.format DateTimeFormatter]))

;; Logging configuration
(defonce ^:private config
  (atom {:enabled true
         :level :info  ;; :debug, :info, :warn, :error
         :output :stdout  ;; :stdout, :stderr, :file
         :file-path nil
         :format :pretty  ;; :pretty, :json, :edn
         :include-args false
         :include-result false
         :max-length 200}))

(defn configure!
  "Configure logging behavior.
  
  Options:
    :enabled       - Enable/disable logging (default: true)
    :level         - Log level: :debug, :info, :warn, :error (default: :info)
    :output        - Output destination: :stdout, :stderr, :file (default: :stdout)
    :file-path     - Path to log file (required if :output is :file)
    :format        - Log format: :pretty, :json, :edn (default: :pretty)
    :include-args  - Include tool arguments in logs (default: false)
    :include-result - Include tool results in logs (default: false)
    :max-length    - Max length for truncated values (default: 200)"
  [opts]
  (swap! config merge opts))

(defn get-config [] @config)

(defn- timestamp []
  (.format (ZonedDateTime/now (ZoneId/systemDefault))
           (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss.SSS")))

(defn- truncate [s max-len]
  (let [s-str (str s)]
    (if (<= (count s-str) max-len)
      s-str
      (str (subs s-str 0 (- max-len 3)) "..."))))

(defn- format-log-entry
  "Format a log entry based on configuration."
  [{:keys [tool-name args result ctx params]} level]
  (let [{:keys [format include-args include-result max-length]} @config
        agent-id (:id ctx)
        status (:status result)]
    (case format
      :pretty
      (str "[" (timestamp) "] "
           (str/upper-case (name level)) " "
           "Agent:" agent-id " "
           "Tool:" tool-name " "
           "Status:" (or status "unknown")
           (when include-args
             (str " Args:" (truncate (pr-str (dissoc args :ctx :params)) max-length)))
           (when include-result
             (str " Result:" (truncate (pr-str result) max-length))))

      :json
      (cheshire.core/generate-string
       (cond-> {:timestamp (timestamp)
                :level (name level)
                :agent-id agent-id
                :tool tool-name
                :status (or status "unknown")}
         include-args (assoc :args (dissoc args :ctx :params))
         include-result (assoc :result result)))

      :edn
      (pr-str
       (cond-> {:timestamp (timestamp)
                :level level
                :agent-id agent-id
                :tool tool-name
                :status (or status "unknown")}
         include-args (assoc :args (dissoc args :ctx :params))
         include-result (assoc :result result))))))

(defn- write-log [message]
  (let [{:keys [output file-path]} @config]
    (case output
      :stdout (println message)
      :stderr (binding [*out* *err*] (println message))
      :file (when file-path
              (spit file-path (str message "\n") :append true)))))

(defn log-tool-execution
  "Hook function that logs tool execution.
  
  This is the main logging hook that should be registered with the hooks system."
  [hook-data]
  (when (:enabled @config)
    (try
      (let [level :info
            message (format-log-entry hook-data level)]
        (write-log message))
      (catch Exception e
        (binding [*out* *err*]
          (println "⚠️  Logging hook failed:" (.getMessage e)))))))

(defn log-tool-error
  "Hook function specifically for logging errors."
  [{:keys [tool-name result ctx] :as hook-data}]
  (when (and (:enabled @config)
             (= :error (:status result)))
    (try
      (let [level :error
            message (format-log-entry hook-data level)]
        (write-log message))
      (catch Exception e
        (binding [*out* *err*]
          (println "⚠️  Error logging hook failed:" (.getMessage e)))))))

(defn register-logging-hooks!
  "Register logging hooks for all tools.
  
  Options:
    :tools - Vector of tool keywords to log (default: all tools)
    :config - Logging configuration map"
  [& {:keys [tools config-opts]}]
  (when config-opts
    (configure! config-opts))

  (require '[pyjama.agent.hooks :as hooks])
  (let [register! (resolve 'pyjama.agent.hooks/register-hook!)
        tool-list (or tools [:write-file :read-files :list-directory
                             :cat-files :discover-codebase])]
    (doseq [tool tool-list]
      (register! tool log-tool-execution))
    (println (str "✓ Registered logging hooks for " (count tool-list) " tools"))))

(defn unregister-logging-hooks!
  "Unregister all logging hooks."
  [& {:keys [tools]}]
  (require '[pyjama.agent.hooks :as hooks])
  (let [unregister! (resolve 'pyjama.agent.hooks/unregister-hook!)
        tool-list (or tools [:write-file :read-files :list-directory
                             :cat-files :discover-codebase])]
    (doseq [tool tool-list]
      (unregister! tool log-tool-execution))
    (println (str "✓ Unregistered logging hooks for " (count tool-list) " tools"))))

(comment
  ;; Example usage

  ;; Configure logging
  (configure! {:enabled true
               :level :info
               :output :file
               :file-path "/tmp/pyjama-tools.log"
               :format :json
               :include-args true
               :include-result true})

  ;; Register logging for all tools
  (register-logging-hooks!)

  ;; Register logging for specific tools only
  (register-logging-hooks! :tools [:write-file :read-files])

  ;; Disable logging
  (configure! {:enabled false})

  ;; Unregister
  (unregister-logging-hooks!))
