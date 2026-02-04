(ns pyjama.agent.init
  "Default initialization for Pyjama agents.
  
  This module provides automatic initialization of common hooks and integrations:
  - Logging hooks for all tool executions
  - Metrics tracking for performance monitoring
  - Shared metrics for cross-process dashboard
  - Optional notifications
  
  Configuration via environment variables:
    PYJAMA_LOG_LEVEL - Log level: debug, info, warn, error (default: info)
    PYJAMA_LOG_OUTPUT - Output: stdout, stderr, file (default: stdout)
    PYJAMA_LOG_FORMAT - Format: pretty, json, edn (default: pretty)
    PYJAMA_NOTIFICATIONS - Enable notifications: true, false (default: false)
    PYJAMA_HOOKS_DISABLED - Disable all hooks: true, false (default: false)")

;; Configuration
(def ^:private default-config
  {:logging true
   :metrics true
   :shared-metrics true
   :notifications false
   :log-level :info
   :log-output :stdout
   :log-format :pretty
   :notify-errors true
   :notify-completion false})

(defn- load-config
  "Load configuration from environment or use defaults."
  []
  (let [env-config {:log-level (keyword (or (System/getenv "PYJAMA_LOG_LEVEL") "info"))
                    :log-output (keyword (or (System/getenv "PYJAMA_LOG_OUTPUT") "stdout"))
                    :log-format (keyword (or (System/getenv "PYJAMA_LOG_FORMAT") "pretty"))
                    :notifications (= "true" (System/getenv "PYJAMA_NOTIFICATIONS"))
                    :hooks-disabled (= "true" (System/getenv "PYJAMA_HOOKS_DISABLED"))}]
    (merge default-config env-config)))

(defn- register-logging-hooks!
  "Register logging hooks for all standard tools."
  [config]
  (try
    (require '[pyjama.agent.hooks.logging :as log])
    (let [configure! (resolve 'pyjama.agent.hooks.logging/configure!)
          register! (resolve 'pyjama.agent.hooks.logging/register-logging-hooks!)]
      (configure! {:level (:log-level config)
                   :output (:log-output config)
                   :format (:log-format config)
                   :include-args false
                   :include-result false})
      (register!)
      (println "✓ Registered logging hooks"))
    (catch Exception e
      (println "⚠️  Failed to enable logging hooks:" (.getMessage e)))))

(defn- register-metrics-hooks!
  "Register metrics hooks for performance tracking."
  [config]
  (try
    (require '[pyjama.agent.hooks.metrics :as metrics])
    (let [register! (resolve 'pyjama.agent.hooks.metrics/register-metrics-hooks!)]
      (register!)
      (println "✓ Registered metrics hooks"))
    (catch Exception e
      (println "⚠️  Failed to enable metrics hooks:" (.getMessage e)))))

(defn- register-shared-metrics-hooks!
  "Register shared metrics hooks for cross-process dashboard monitoring."
  [config]
  (try
    (require '[pyjama.agent.hooks.shared-metrics :as shared]
             '[pyjama.agent.hooks :as hooks])
    (let [shared-hook (resolve 'pyjama.agent.hooks.shared-metrics/shared-metrics-hook)
          register-hook! (resolve 'pyjama.agent.hooks/register-hook!)]
      ;; Register shared metrics hook for all standard tools
      (doseq [tool [:write-file :read-files :list-directory :cat-files :discover-codebase
                    :llm-call :template-call]]
        (register-hook! tool shared-hook))
      (println "✓ Registered shared metrics hooks for dashboard"))
    (catch Exception e
      (println "⚠️  Failed to enable shared metrics:" (.getMessage e)))))

(defn- register-notification-hooks!
  "Register notification hooks for errors and completions."
  [config]
  (try
    (require '[pyjama.agent.hooks.notifications :as notif])
    (let [register-handler! (resolve 'pyjama.agent.hooks.notifications/register-handler!)
          register-hooks! (resolve 'pyjama.agent.hooks.notifications/register-notification-hooks!)
          console-handler (resolve 'pyjama.agent.hooks.notifications/console-handler)]
      (register-handler! :console console-handler)
      (register-hooks! :on-error (:notify-errors config)
                       :on-completion (:notify-completion config))
      (println "✓ Registered notification hooks"))
    (catch Exception e
      (println "⚠️  Failed to enable notification hooks:" (.getMessage e)))))

(defn init!
  "Initialize default Pyjama agent hooks and integrations.
  
  This is called automatically by the Pyjama CLI before running any agent.
  Projects can provide their own init! function which will be called after this.
  
  To disable all hooks, set PYJAMA_HOOKS_DISABLED=true"
  []
  (let [config (load-config)]

    (when-not (:hooks-disabled config)
      (println "🔧 Initializing Pyjama agent hooks...")

      ;; Logging hooks
      (when (:logging config)
        (register-logging-hooks! config))

      ;; Metrics hooks
      (when (:metrics config)
        (register-metrics-hooks! config))

      ;; Shared metrics (for dashboard)
      (when (:shared-metrics config)
        (register-shared-metrics-hooks! config))

      ;; Notification hooks
      (when (:notifications config)
        (register-notification-hooks! config))

      (println "✓ Pyjama initialization complete"))))

(defn shutdown!
  "Clean shutdown of Pyjama agent.
  
  Marks agent as complete in shared metrics and prints final metrics summary."
  []
  ;; Mark agent as complete in shared metrics
  (try
    (require '[pyjama.agent.hooks.shared-metrics :as shared])
    (let [complete-fn (resolve 'pyjama.agent.hooks.shared-metrics/record-agent-complete!)]
      ;; Try to get agent ID from system property
      (when-let [agent-id (System/getProperty "pyjama.agent.id")]
        (complete-fn agent-id)))
    (catch Exception e
      nil))

  ;; Print final metrics
  (try
    (require '[pyjama.agent.hooks.metrics :as metrics])
    (let [print-summary! (resolve 'pyjama.agent.hooks.metrics/print-metrics-summary)]
      (println "\n📊 Final Metrics:")
      (print-summary!))
    (catch Exception e
      ;; Metrics might not be enabled
      nil)))

(comment
  ;; Initialize with defaults
  (init!)

  ;; Shutdown
  (shutdown!))
