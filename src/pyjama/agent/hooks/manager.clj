(ns pyjama.agent.hooks.manager
  "Centralized hooks management system.
  
  Provides a unified interface for managing all hook types:
  - Logging
  - Metrics
  - Notifications
  - Custom hooks
  
  Simplifies setup and configuration of the entire hooks ecosystem."
  (:require [pyjama.agent.hooks :as hooks]))

(defn enable-all-hooks!
  "Enable all available hooks with sensible defaults.
  
  Options:
    :logging       - Enable logging hooks (default: true)
    :metrics       - Enable metrics hooks (default: true)
    :notifications - Enable notification hooks (default: false)
    :auto-indexing - Enable auto-indexing hooks (default: true)
    
    Logging options:
    :log-level     - :debug, :info, :warn, :error (default: :info)
    :log-output    - :stdout, :stderr, :file (default: :stdout)
    :log-file      - Path to log file (if :log-output is :file)
    :log-format    - :pretty, :json, :edn (default: :pretty)
    
    Notification options:
    :notify-errors - Notify on errors (default: true)
    :notify-completion - Notify on completion (default: false)
    :notify-handlers - Vector of handler IDs to enable (default: [:console])"
  [& {:keys [logging metrics notifications auto-indexing
             log-level log-output log-file log-format
             notify-errors notify-completion notify-handlers]
      :or {logging true
           metrics true
           notifications false
           auto-indexing true
           log-level :info
           log-output :stdout
           log-format :pretty
           notify-errors true
           notify-completion false
           notify-handlers [:console]}}]

  (println "\n🔧 Enabling Pyjama Hooks System...")

  ;; Logging
  (when logging
    (try
      (require '[pyjama.agent.hooks.logging :as log])
      (let [configure! (resolve 'pyjama.agent.hooks.logging/configure!)
            register! (resolve 'pyjama.agent.hooks.logging/register-logging-hooks!)]
        (configure! {:level log-level
                     :output log-output
                     :file-path log-file
                     :format log-format})
        (register!))
      (catch Exception e
        (println "⚠️  Failed to enable logging hooks:" (.getMessage e)))))

  ;; Metrics
  (when metrics
    (try
      (require '[pyjama.agent.hooks.metrics :as metrics])
      (let [register! (resolve 'pyjama.agent.hooks.metrics/register-metrics-hooks!)]
        (register!))
      (catch Exception e
        (println "⚠️  Failed to enable metrics hooks:" (.getMessage e)))))

  ;; Notifications
  (when notifications
    (try
      (require '[pyjama.agent.hooks.notifications :as notif])
      (let [register-handler! (resolve 'pyjama.agent.hooks.notifications/register-handler!)
            register-hooks! (resolve 'pyjama.agent.hooks.notifications/register-notification-hooks!)
            console-handler (resolve 'pyjama.agent.hooks.notifications/console-handler)]

        ;; Register handlers
        (when (some #{:console} notify-handlers)
          (register-handler! :console console-handler))

        ;; Register hooks
        (register-hooks! :on-error notify-errors
                         :on-completion notify-completion))
      (catch Exception e
        (println "⚠️  Failed to enable notification hooks:" (.getMessage e)))))

  ;; Auto-indexing (project-specific)
  (when auto-indexing
    (try
      (require '[codebase-analyzer.auto-indexing :as auto-indexing])
      (let [register! (resolve 'codebase-analyzer.auto-indexing/register-auto-indexing!)]
        (register!))
      (catch Exception e
        ;; Auto-indexing is optional (project-specific)
        nil)))

  (println "✅ Hooks system enabled\n"))

(defn disable-all-hooks!
  "Disable all hooks."
  []
  (println "\n🛑 Disabling Pyjama Hooks System...")

  ;; Clear all hooks
  (hooks/clear-hooks!)

  (println "✅ All hooks disabled\n"))

(defn print-hooks-status
  "Print the current status of all hooks."
  []
  (println "\n╔════════════════════════════════════════════════════════════════╗")
  (println "║                    HOOKS SYSTEM STATUS                        ║")
  (println "╚════════════════════════════════════════════════════════════════╝\n")

  (let [all-tools [:write-file :read-files :list-directory :cat-files
                   :discover-codebase :index-report]]
    (doseq [tool all-tools]
      (let [tool-hooks (hooks/get-hooks tool)
            count (count tool-hooks)]
        (when (pos? count)
          (println (str "🔧 " tool ": " count " hook(s) registered"))))))

  (println "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"))

(defn show-metrics
  "Show current metrics summary."
  []
  (try
    (require '[pyjama.agent.hooks.metrics :as metrics])
    (let [print-summary! (resolve 'pyjama.agent.hooks.metrics/print-metrics-summary)]
      (print-summary!))
    (catch Exception e
      (println "⚠️  Metrics not available:" (.getMessage e)))))

(comment
  ;; Example usage

  ;; Enable all hooks with defaults
  (enable-all-hooks!)

  ;; Enable with custom configuration
  (enable-all-hooks! :logging true
                     :metrics true
                     :notifications true
                     :log-level :debug
                     :log-output :file
                     :log-file "/tmp/pyjama.log"
                     :log-format :json
                     :notify-errors true
                     :notify-completion true)

  ;; Check status
  (print-hooks-status)

  ;; View metrics
  (show-metrics)

  ;; Disable all
  (disable-all-hooks!))
