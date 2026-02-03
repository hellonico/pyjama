(ns pyjama.agent.hooks.shared-metrics
  "Shared metrics storage using file-based persistence.
  
  Allows multiple Pyjama processes to share metrics data
  by writing to a common JSON file at ~/.pyjama/metrics.json
  
  This enables the dashboard to monitor agents running in
  separate processes."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [java.io File]
           [java.nio.file Files Paths StandardOpenOption]
           [java.nio.file.attribute FileAttribute]))

(def ^:private metrics-file
  (str (System/getProperty "user.home") "/.pyjama/metrics.json"))

(def ^:private lock-file
  (str (System/getProperty "user.home") "/.pyjama/metrics.lock"))

(defn- ensure-metrics-dir! []
  "Ensure the ~/.pyjama directory exists."
  (let [dir (File. (str (System/getProperty "user.home") "/.pyjama"))]
    (when-not (.exists dir)
      (.mkdirs dir))))

(defn- with-file-lock
  "Execute function with file lock to prevent concurrent writes."
  [f]
  (ensure-metrics-dir!)
  (let [lock (File. lock-file)]
    (try
      ;; Simple lock using file creation
      (while (not (.createNewFile lock))
        (Thread/sleep 10))
      (f)
      (finally
        (.delete lock)))))

(defn write-metrics!
  "Write metrics to shared file (thread-safe)."
  [metrics-data]
  (try
    (with-file-lock
      (fn []
        (spit metrics-file (json/write-str metrics-data))))
    (catch Exception e
      (binding [*out* *err*]
        (println "⚠️  Failed to write shared metrics:" (.getMessage e))))))

(defn read-metrics
  "Read metrics from shared file."
  []
  (try
    (when (.exists (File. metrics-file))
      (json/read-str (slurp metrics-file) :key-fn keyword))
    (catch Exception e
      nil)))

(defn update-metrics!
  "Update shared metrics with new data (thread-safe)."
  [update-fn]
  (with-file-lock
    (fn []
      (let [current (or (read-metrics) {})
            updated (update-fn current)]
        (spit metrics-file (json/write-str updated))
        updated))))

(defn record-agent-start!
  "Record that an agent has started."
  [agent-id]
  (update-metrics!
   (fn [metrics]
     (assoc-in metrics [:agents agent-id]
               {:status "running"
                :start-time (System/currentTimeMillis)
                :last-seen (System/currentTimeMillis)
                :end-time nil
                :steps []}))))

(defn record-agent-activity!
  "Record agent activity (tool execution)."
  [{:keys [agent-id tool-name status]}]
  (let [timestamp (System/currentTimeMillis)]
    (update-metrics!
     (fn [metrics]
       (-> metrics
           ;; Update agent last-seen
           (assoc-in [:agents agent-id :last-seen] timestamp)
           (assoc-in [:agents agent-id :status] "running")

           ;; Add to recent logs
           (update :recent-logs
                   (fn [logs]
                     (let [new-log {:timestamp timestamp
                                    :agent-id agent-id
                                    :tool tool-name
                                    :status (name (or status :unknown))}
                           logs (or logs [])]
                       (vec (take-last 100 (conj logs new-log)))))))))))

(defn record-agent-complete!
  "Record that an agent has completed."
  [agent-id]
  (update-metrics!
   (fn [metrics]
     (let [current-time (System/currentTimeMillis)
           agent (get-in metrics [:agents agent-id])
           start-time (:start-time agent)
           duration (when start-time
                      (- current-time start-time))]
       (-> metrics
           (assoc-in [:agents agent-id :status] "completed")
           (assoc-in [:agents agent-id :end-time] current-time)

           ;; Update global metrics
           (update-in [:global :count] (fnil inc 0))
           (update-in [:global :success] (fnil inc 0))
           (update-in [:global :total-duration] (fnil + 0) (or duration 0)))))))

(defn record-tool-execution!
  "Record tool execution metrics."
  [{:keys [tool-name agent-id status duration-ms]}]
  (update-metrics!
   (fn [metrics]
     (let [success? (= status :ok)]
       (-> metrics
           ;; Update tool metrics
           (update-in [:tools tool-name :count] (fnil inc 0))
           (update-in [:tools tool-name (if success? :success :error)] (fnil inc 0))
           (update-in [:tools tool-name :total-duration] (fnil + 0) (or duration-ms 0))
           (update-in [:tools tool-name :durations] (fnil conj []) (or duration-ms 0))

           ;; Update global metrics
           (update-in [:global :count] (fnil inc 0))
           (update-in [:global (if success? :success :error)] (fnil inc 0))
           (update-in [:global :total-duration] (fnil + 0) (or duration-ms 0)))))))

(defn record-step-start!
  "Record that an agent has started executing a step."
  [agent-id step-id]
  (when step-id  ;; Only record if step-id is not nil
    (let [timestamp (System/currentTimeMillis)]
      (update-metrics!
       (fn [metrics]
         (-> metrics
             ;; Mark step as current
             (assoc-in [:agents agent-id :current-step] (name step-id))
             (assoc-in [:agents agent-id :current-step-start] timestamp)

             ;; Add to step history
             (update-in [:agents agent-id :steps]
                        (fn [steps]
                          (let [steps (or steps [])]
                            (conj steps {:step-id (name step-id)
                                         :status "running"
                                         :start-time timestamp}))))))))))

(defn record-step-complete!
  "Record that an agent has completed a step."
  [agent-id step-id status]
  (let [timestamp (System/currentTimeMillis)]
    (update-metrics!
     (fn [metrics]
       (let [steps (get-in metrics [:agents agent-id :steps] [])
             last-step-idx (dec (count steps))]
         (if (>= last-step-idx 0)
           (-> metrics
               ;; Clear current step
               (assoc-in [:agents agent-id :current-step] nil)
               (assoc-in [:agents agent-id :current-step-start] nil)

               ;; Update last step in history using update-in to maintain vector
               (update-in [:agents agent-id :steps last-step-idx]
                          (fn [step]
                            (assoc step
                                   :status (name (or status :ok))
                                   :end-time timestamp)))

               ;; Add to recent activity logs
               (update :recent-logs
                       (fn [logs]
                         (let [new-log {:timestamp timestamp
                                        :agent-id agent-id
                                        :tool (name step-id)
                                        :status (name (or status :ok))}
                               logs (or logs [])]
                           (vec (take-last 100 (conj logs new-log)))))))
           metrics))))))

(defn record-workflow-info!
  "Record workflow information (all steps) for an agent."
  [agent-id workflow-steps]
  (update-metrics!
   (fn [metrics]
     (assoc-in metrics [:agents agent-id :workflow]
               {:total-steps (count workflow-steps)
                :steps (mapv name workflow-steps)}))))

(defn get-dashboard-data
  "Get formatted dashboard data from shared metrics."
  []
  (let [metrics (or (read-metrics) {})
        global (:global metrics)
        tools (:tools metrics)
        agents (:agents metrics)]
    {:agents (or agents {})
     :metrics {:global (merge {:count 0 :success 0 :error 0 :total-duration 0}
                              global
                              {:success-rate (if (and global (pos? (:count global)))
                                               (double (/ (:success global) (:count global)))
                                               0.0)
                               :avg-duration-ms (if (and global (pos? (:count global)))
                                                  (double (/ (:total-duration global) (:count global)))
                                                  0.0)
                               :throughput (if (and global (:total-duration global) (pos? (:total-duration global)))
                                             (double (/ (* (:count global) 1000.0) (:total-duration global)))
                                             0.0)})
               :tools (or tools {})
               :agents (or agents {})}
     :recent-logs (or (:recent-logs metrics) [])
     :hooks {:registered {}}  ;; Hooks are process-local
     :timestamp (System/currentTimeMillis)}))

(defn clear-metrics!
  "Clear all shared metrics."
  []
  (write-metrics! {}))

;; Hook function that records to shared metrics
(defn shared-metrics-hook
  "Hook that records tool execution to shared metrics."
  [{:keys [tool-name ctx result]}]
  (when-let [agent-id (:id ctx)]
    ;; Ensure agent is registered (idempotent - only sets start-time if not already set)
    (update-metrics!
     (fn [metrics]
       (if-not (get-in metrics [:agents agent-id :start-time])
         (assoc-in metrics [:agents agent-id]
                   {:status "running"
                    :start-time (System/currentTimeMillis)
                    :last-seen (System/currentTimeMillis)})
         metrics)))

    ;; Record the activity
    (record-agent-activity! {:agent-id agent-id
                             :tool-name tool-name
                             :status (:status result)})))

;; Step tracking hook (call this manually from agent execution)
(defn track-step-execution!
  "Track step execution for an agent.
  
  Call this when a step starts executing to record it in shared metrics.
  
  Args:
    agent-id - The agent ID
    step-id - The step ID being executed"
  [agent-id step-id]
  (record-step-start! agent-id step-id))

(comment
  ;; Test writing
  (write-metrics! {:test "data"})

  ;; Test reading
  (read-metrics)

  ;; Test recording activity
  (record-agent-start! "test-agent")
  (record-agent-activity! {:agent-id "test-agent"
                           :tool-name :write-file
                           :status :ok})
  (record-agent-complete! "test-agent")

  ;; Get dashboard data
  (get-dashboard-data)

  ;; Clear all
  (clear-metrics!))
