(ns pyjama.agent.hooks.metrics
  "Metrics and performance tracking hooks for agent tools.
  
  Automatically tracks execution counts, durations, success/failure rates,
  and provides statistical summaries."
  (:require [clojure.string :as str])
  (:import [java.time Instant Duration]))

;; Metrics storage
(defonce ^:private metrics
  (atom {:tools {}       ;; Per-tool metrics
         :agents {}      ;; Per-agent metrics
         :global {}      ;; Global metrics
         :start-time (Instant/now)}))

;; Execution timing storage (for tracking in-flight operations)
(defonce ^:private executions (atom {}))

(defn reset-metrics!
  "Reset all metrics to initial state."
  []
  (reset! metrics {:tools {}
                   :agents {}
                   :global {}
                   :start-time (Instant/now)})
  (reset! executions {}))

(defn- generate-execution-id []
  (str (java.util.UUID/randomUUID)))

(defn- record-start!
  "Record the start of a tool execution."
  [tool-name agent-id]
  (let [exec-id (generate-execution-id)
        start-time (Instant/now)]
    (swap! executions assoc exec-id {:tool tool-name
                                     :agent agent-id
                                     :start-time start-time})
    exec-id))

(defn- record-end!
  "Record the end of a tool execution and update metrics."
  [exec-id status]
  (when-let [exec-data (get @executions exec-id)]
    (let [{:keys [tool agent start-time]} exec-data
          end-time (Instant/now)
          duration-ms (.toMillis (Duration/between start-time end-time))
          success? (= :ok status)]

      ;; Update tool metrics
      (swap! metrics update-in [:tools tool]
             (fn [m]
               (-> (or m {:count 0 :success 0 :error 0 :total-duration-ms 0 :durations []})
                   (update :count inc)
                   (update (if success? :success :error) inc)
                   (update :total-duration-ms + duration-ms)
                   (update :durations conj duration-ms))))

      ;; Update agent metrics
      (swap! metrics update-in [:agents agent]
             (fn [m]
               (-> (or m {:count 0 :success 0 :error 0 :total-duration-ms 0})
                   (update :count inc)
                   (update (if success? :success :error) inc)
                   (update :total-duration-ms + duration-ms))))

      ;; Update global metrics
      (swap! metrics update :global
             (fn [m]
               (-> (or m {:count 0 :success 0 :error 0 :total-duration-ms 0})
                   (update :count inc)
                   (update (if success? :success :error) inc)
                   (update :total-duration-ms + duration-ms))))

      ;; Clean up execution tracking
      (swap! executions dissoc exec-id))))

(defn track-tool-execution
  "Hook function that tracks tool execution metrics."
  [{:keys [tool-name result ctx]}]
  (try
    (let [agent-id (or (:id ctx) "unknown")
          status (or (:status result) :ok)]  ;; Default to :ok if no status
      ;; For now, we'll record both start and end in the same hook
      ;; In the future, we could have separate pre/post hooks
      (let [exec-id (record-start! tool-name agent-id)]
        ;; Simulate immediate completion (in real impl, this would be in post-hook)
        (record-end! exec-id status)))
    (catch Exception e
      (binding [*out* *err*]
        (println "⚠️  Metrics hook failed:" (.getMessage e))
        (println "   tool-name:" tool-name)
        (println "   result:" result)
        (println "   ctx:" ctx)))))

(defn- calculate-stats [durations]
  (when (seq durations)
    (let [sorted (sort durations)
          count (count sorted)
          sum (reduce + sorted)
          avg (/ sum count)
          median (nth sorted (quot count 2))
          p95 (nth sorted (int (* count 0.95)))
          p99 (nth sorted (int (* count 0.99)))
          min-val (first sorted)
          max-val (last sorted)]
      {:count count
       :min min-val
       :max max-val
       :avg (double avg)
       :median median
       :p95 p95
       :p99 p99})))

(defn get-tool-metrics
  "Get metrics for a specific tool."
  [tool-name]
  (when-let [m (get-in @metrics [:tools tool-name])]
    (let [stats (calculate-stats (:durations m))]
      (-> m
          (dissoc :durations)
          (merge stats)
          (assoc :success-rate (if (pos? (:count m))
                                 (double (/ (:success m) (:count m)))
                                 0.0))))))

(defn get-agent-metrics
  "Get metrics for a specific agent."
  [agent-id]
  (when-let [m (get-in @metrics [:agents agent-id])]
    (assoc m :success-rate (if (pos? (:count m))
                             (double (/ (:success m) (:count m)))
                             0.0)
           :avg-duration-ms (if (pos? (:count m))
                              (double (/ (:total-duration-ms m) (:count m)))
                              0.0))))

(defn get-global-metrics
  "Get global metrics across all tools and agents."
  []
  (let [m (or (:global @metrics) {:count 0 :success 0 :error 0 :total-duration-ms 0})
        uptime-ms (.toMillis (Duration/between (:start-time @metrics) (Instant/now)))]
    (assoc m :success-rate (if (and (:count m) (pos? (:count m)))
                             (double (/ (:success m) (:count m)))
                             0.0)
           :avg-duration-ms (if (and (:count m) (pos? (:count m)))
                              (double (/ (:total-duration-ms m) (:count m)))
                              0.0)
           :uptime-ms uptime-ms
           :throughput (if (pos? uptime-ms)
                         (double (/ (or (:count m) 0) (/ uptime-ms 1000.0)))
                         0.0))))

(defn get-all-metrics
  "Get all metrics in a comprehensive report."
  []
  {:global (get-global-metrics)
   :tools (into {} (map (fn [[k _]] [k (get-tool-metrics k)]) (:tools @metrics)))
   :agents (into {} (map (fn [[k _]] [k (get-agent-metrics k)]) (:agents @metrics)))})

(defn print-metrics-summary
  "Print a human-readable metrics summary."
  []
  (let [all (get-all-metrics)]
    (println "\n╔════════════════════════════════════════════════════════════════╗")
    (println "║                    PYJAMA METRICS SUMMARY                      ║")
    (println "╚════════════════════════════════════════════════════════════════╝\n")

    ;; Global metrics
    (println "📊 Global Metrics:")
    (let [g (:global all)]
      (println (str "   Total Executions: " (:count g)))
      (println (str "   Success: " (:success g) " (" (format "%.1f%%" (* 100 (:success-rate g))) ")"))
      (println (str "   Errors: " (:error g)))
      (println (str "   Avg Duration: " (format "%.2fms" (:avg-duration-ms g))))
      (println (str "   Throughput: " (format "%.2f ops/sec" (:throughput g))))
      (println (str "   Uptime: " (format "%.2fs" (/ (:uptime-ms g) 1000.0)))))

    ;; Per-tool metrics
    (when (seq (:tools all))
      (println "\n🔧 Tool Metrics:")
      (doseq [[tool m] (sort-by (comp :count second) > (:tools all))]
        (println (str "   " tool ":"))
        (println (str "     Executions: " (:count m)))
        (println (str "     Success Rate: " (format "%.1f%%" (* 100 (:success-rate m)))))
        (when (:avg m)
          (println (str "     Duration: avg=" (format "%.2fms" (:avg m))
                        " median=" (format "%.2fms" (:median m))
                        " p95=" (format "%.2fms" (:p95 m)))))))

    ;; Per-agent metrics
    (when (seq (:agents all))
      (println "\n🤖 Agent Metrics:")
      (doseq [[agent m] (sort-by (comp :count second) > (:agents all))]
        (println (str "   " agent ":"))
        (println (str "     Executions: " (:count m)))
        (println (str "     Success Rate: " (format "%.1f%%" (* 100 (:success-rate m)))))
        (println (str "     Avg Duration: " (format "%.2fms" (:avg-duration-ms m))))))

    (println "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")))

(defn register-metrics-hooks!
  "Register metrics tracking hooks for all tools."
  [& {:keys [tools]}]
  (require '[pyjama.agent.hooks :as hooks])
  (let [register! (resolve 'pyjama.agent.hooks/register-hook!)
        tool-list (or tools [:write-file :read-files :list-directory
                             :cat-files :discover-codebase])]
    (doseq [tool tool-list]
      (register! tool track-tool-execution))
    (println (str "✓ Registered metrics hooks for " (count tool-list) " tools"))))

(defn unregister-metrics-hooks!
  "Unregister all metrics hooks."
  [& {:keys [tools]}]
  (require '[pyjama.agent.hooks :as hooks])
  (let [unregister! (resolve 'pyjama.agent.hooks/unregister-hook!)
        tool-list (or tools [:write-file :read-files :list-directory
                             :cat-files :discover-codebase])]
    (doseq [tool tool-list]
      (unregister! tool track-tool-execution))
    (println (str "✓ Unregistered metrics hooks for " (count tool-list) " tools"))))

(comment
  ;; Example usage

  ;; Register metrics tracking
  (register-metrics-hooks!)

  ;; Run some agents...

  ;; View metrics
  (print-metrics-summary)

  ;; Get specific metrics
  (get-tool-metrics :write-file)
  (get-agent-metrics "software-versions-v2")
  (get-global-metrics)

  ;; Reset metrics
  (reset-metrics!)

  ;; Unregister
  (unregister-metrics-hooks!))
