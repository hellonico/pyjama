(ns test-hooks-ecosystem
  "Comprehensive test for the Pyjama hooks ecosystem"
  (:require [pyjama.agent.hooks :as hooks]
            [pyjama.agent.hooks.logging :as log]
            [pyjama.agent.hooks.metrics :as metrics]
            [pyjama.agent.hooks.notifications :as notif]
            [pyjama.agent.hooks.manager :as manager]))

(defn test-core-hooks []
  (println "\n=== Testing Core Hooks ===\n")

  ;; Test post-execution hook
  (println "1. Testing post-execution hooks...")
  (hooks/register-hook! :test-tool
                        (fn [{:keys [tool-name result]}]
                          (println (str "   ✓ Post-hook executed for " tool-name " with status " (:status result)))))

  (hooks/run-hooks! :test-tool
                    {:tool-name :test-tool
                     :result {:status :ok}
                     :ctx {:id "test"}
                     :params {}})

  ;; Test pre-execution hook
  (println "\n2. Testing pre-execution hooks...")
  (hooks/register-pre-hook! :test-tool
                            (fn [hook-data]
                              (println "   ✓ Pre-hook executed, validating inputs...")
                              hook-data))

  (hooks/run-pre-hooks! :test-tool
                        {:tool-name :test-tool
                         :args {:test "value"}
                         :ctx {:id "test"}
                         :params {}})

  ;; Test hook counts
  (println "\n3. Checking hook counts...")
  (let [post-count (count (hooks/get-hooks :test-tool))
        pre-count (count (hooks/get-pre-hooks :test-tool))]
    (println (str "   Post-hooks: " post-count))
    (println (str "   Pre-hooks: " pre-count)))

  ;; Clean up
  (hooks/clear-hooks! :test-tool)
  (println "\n✅ Core hooks test complete"))

(defn test-logging-hooks []
  (println "\n=== Testing Logging Hooks ===\n")

  ;; Configure logging
  (println "1. Configuring logging...")
  (log/configure! {:enabled true
                   :level :info
                   :output :stdout
                   :format :pretty
                   :include-args false
                   :include-result false})
  (println "   ✓ Logging configured")

  ;; Register logging hooks
  (println "\n2. Registering logging hooks...")
  (log/register-logging-hooks! :tools [:test-tool])

  ;; Test logging
  (println "\n3. Testing log output...")
  (hooks/run-hooks! :test-tool
                    {:tool-name :test-tool
                     :args {:test "value"}
                     :result {:status :ok}
                     :ctx {:id "test-agent"}
                     :params {}})

  ;; Clean up
  (log/unregister-logging-hooks! :tools [:test-tool])
  (println "\n✅ Logging hooks test complete"))

(defn test-metrics-hooks []
  (println "\n=== Testing Metrics Hooks ===\n")

  ;; Reset metrics
  (println "1. Resetting metrics...")
  (metrics/reset-metrics!)
  (println "   ✓ Metrics reset")

  ;; Register metrics hooks
  (println "\n2. Registering metrics hooks...")
  (metrics/register-metrics-hooks! :tools [:test-tool])

  ;; Simulate some executions
  (println "\n3. Simulating tool executions...")
  (dotimes [i 5]
    (hooks/run-hooks! :test-tool
                      {:tool-name :test-tool
                       :result {:status (if (< i 4) :ok :error)}
                       :ctx {:id "test-agent"}
                       :params {}}))
  (println "   ✓ Simulated 5 executions (4 success, 1 error)")

  ;; Check metrics
  (println "\n4. Checking metrics...")
  (let [tool-metrics (metrics/get-tool-metrics :test-tool)
        global-metrics (metrics/get-global-metrics)]
    (println (str "   Tool executions: " (:count tool-metrics)))
    (println (str "   Success rate: " (format "%.1f%%" (* 100 (:success-rate tool-metrics)))))
    (println (str "   Global executions: " (:count global-metrics))))

  ;; Clean up
  (metrics/unregister-metrics-hooks! :tools [:test-tool])
  (println "\n✅ Metrics hooks test complete"))

(defn test-notification-hooks []
  (println "\n=== Testing Notification Hooks ===\n")

  ;; Register console handler
  (println "1. Registering console handler...")
  (notif/register-handler! :console notif/console-handler)
  (println "   ✓ Handler registered")

  ;; Register notification hooks
  (println "\n2. Registering notification hooks...")
  (notif/register-notification-hooks! :on-error true
                                      :on-completion true
                                      :tools [:test-tool])

  ;; Test success notification
  (println "\n3. Testing success notification...")
  (hooks/run-hooks! :test-tool
                    {:tool-name :test-tool
                     :result {:status :ok}
                     :ctx {:id "test-agent"}
                     :params {}})

  ;; Test error notification
  (println "\n4. Testing error notification...")
  (hooks/run-hooks! :test-tool
                    {:tool-name :test-tool
                     :result {:status :error :error "Test error"}
                     :ctx {:id "test-agent"}
                     :params {}})

  ;; Clean up
  (notif/unregister-notification-hooks!)
  (notif/unregister-handler! :console)
  (println "\n✅ Notification hooks test complete"))

(defn test-hooks-manager []
  (println "\n=== Testing Hooks Manager ===\n")

  ;; Enable all hooks
  (println "1. Enabling all hooks...")
  (manager/enable-all-hooks! :logging false  ;; Disable to reduce noise
                             :metrics true
                             :notifications false)

  ;; Check status
  (println "\n2. Checking hooks status...")
  (manager/print-hooks-status)

  ;; Simulate some work
  (println "3. Simulating work...")
  (dotimes [i 3]
    (hooks/run-hooks! :write-file
                      {:tool-name :write-file
                       :result {:status :ok :file (str "/tmp/test-" i ".md")}
                       :ctx {:id "test-agent"}
                       :params {}}))

  ;; Show metrics
  (println "\n4. Showing metrics...")
  (manager/show-metrics)

  ;; Disable all
  (println "5. Disabling all hooks...")
  (manager/disable-all-hooks!)

  (println "\n✅ Hooks manager test complete"))

(defn run-all-tests []
  (println "\n╔════════════════════════════════════════════════════════════════╗")
  (println "║           PYJAMA HOOKS ECOSYSTEM TEST SUITE                    ║")
  (println "╚════════════════════════════════════════════════════════════════╝")

  (test-core-hooks)
  (test-logging-hooks)
  (test-metrics-hooks)
  (test-notification-hooks)
  (test-hooks-manager)

  (println "\n╔════════════════════════════════════════════════════════════════╗")
  (println "║                  ALL TESTS COMPLETE! 🎉                        ║")
  (println "╚════════════════════════════════════════════════════════════════╝\n"))

(comment
  ;; Run all tests
  (run-all-tests)

  ;; Run individual tests
  (test-core-hooks)
  (test-logging-hooks)
  (test-metrics-hooks)
  (test-notification-hooks)
  (test-hooks-manager))
