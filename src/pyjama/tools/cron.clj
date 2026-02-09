(ns pyjama.tools.cron
  "Cron-like scheduling tool for pyjama agents."
  (:require [clojure.string :as str])
  (:import [java.util.concurrent Executors TimeUnit]
           [java.time ZonedDateTime ZoneId]
           [java.time.temporal ChronoUnit]))

;; Global registry of scheduled tasks
(defonce ^:private scheduled-tasks (atom {}))

;; Global executor service
(defonce ^:private scheduler
  (delay (Executors/newScheduledThreadPool 4)))

(defn- parse-cron-simple
  "Parse a simple cron-like expression: '*/5 * * * *' or '@hourly', '@daily', etc.
   Returns a map with :type and scheduling info.
   
   Supported formats:
   - @hourly - Run every hour
   - @daily - Run every day at midnight
   - @weekly - Run every week on Sunday at midnight
   - */N * * * * - Run every N minutes
   - 0 * * * * - Run every hour
   - 0 0 * * * - Run every day at midnight
   
   Note: This is a simplified parser, not full cron syntax."
  [expr]
  (let [expr (str/trim expr)]
    (cond
      (= expr "@hourly")
      {:type :interval :value 1 :unit :hours}

      (= expr "@daily")
      {:type :daily :hour 0 :minute 0}

      (= expr "@weekly")
      {:type :weekly :day-of-week 7 :hour 0 :minute 0}

      ;; */N * * * * - every N minutes
      (re-matches #"\*/(\d+)\s+\*\s+\*\s+\*\s+\*" expr)
      (let [[_ n] (re-matches #"\*/(\d+)\s+\*\s+\*\s+\*\s+\*" expr)]
        {:type :interval :value (Long/parseLong n) :unit :minutes})

      ;; 0 */N * * * - every N hours
      (re-matches #"0\s+\*/(\d+)\s+\*\s+\*\s+\*" expr)
      (let [[_ n] (re-matches #"0\s+\*/(\d+)\s+\*\s+\*\s+\*" expr)]
        {:type :interval :value (Long/parseLong n) :unit :hours})

      ;; 0 0 * * * - daily at midnight
      (= expr "0 0 * * *")
      {:type :daily :hour 0 :minute 0}

      ;; M H * * * - daily at specific time
      (re-matches #"(\d+)\s+(\d+)\s+\*\s+\*\s+\*" expr)
      (let [[_ m h] (re-matches #"(\d+)\s+(\d+)\s+\*\s+\*\s+\*" expr)]
        {:type :daily :hour (Long/parseLong h) :minute (Long/parseLong m)})

      :else
      {:type :error :message (str "Unsupported cron expression: " expr)})))

(defn- calculate-initial-delay
  "Calculate the initial delay in seconds until the next scheduled time."
  [schedule-info]
  (let [now (ZonedDateTime/now (ZoneId/systemDefault))]
    (case (:type schedule-info)
      :interval
      0 ; Start immediately for interval-based schedules

      :daily
      (let [target-hour (:hour schedule-info 0)
            target-minute (:minute schedule-info 0)
            today-target (.truncatedTo
                          (.withHour (.withMinute now target-minute) target-hour)
                          ChronoUnit/MINUTES)
            target (if (.isAfter now today-target)
                     (.plusDays today-target 1)
                     today-target)]
        (.between ChronoUnit/SECONDS now target))

      :weekly
      (let [target-dow (:day-of-week schedule-info 7)
            target-hour (:hour schedule-info 0)
            target-minute (:minute schedule-info 0)
            current-dow (.getValue (.getDayOfWeek now))
            days-until-target (mod (- target-dow current-dow) 7)
            target (.plusDays
                    (.truncatedTo
                     (.withHour (.withMinute now target-minute) target-hour)
                     ChronoUnit/MINUTES)
                    days-until-target)]
        (.between ChronoUnit/SECONDS now target))

      0)))

(defn- get-period
  "Get the period in seconds for repeating schedules."
  [schedule-info]
  (case (:type schedule-info)
    :interval
    (let [value (:value schedule-info)
          unit (:unit schedule-info)]
      (case unit
        :minutes (* value 60)
        :hours (* value 3600)
        :days (* value 86400)
        value))

    :daily
    86400 ; 24 hours

    :weekly
    604800 ; 7 days

    0))

(defn schedule-task
  "Schedule a recurring task using a cron-like expression.
   
   Args:
     :id - Unique identifier for this task
     :schedule - Cron expression (e.g., '*/5 * * * *' for every 5 minutes, '@hourly', '@daily')
     :task - Function to execute (takes no args)
     :description - Optional description of the task
   
   Returns:
     {:status :ok/:error
      :id - Task ID
      :schedule - The cron expression
      :next-run - Timestamp of next scheduled run}"
  [{:keys [id schedule task description] :as _args}]
  (try
    (let [schedule-info (parse-cron-simple schedule)]
      (if (= (:type schedule-info) :error)
        schedule-info
        (let [initial-delay (calculate-initial-delay schedule-info)
              period (get-period schedule-info)
              safe-task (fn []
                          (try
                            (task)
                            (catch Exception e
                              (binding [*out* *err*]
                                (println (str "Error in scheduled task " id ": " (.getMessage e)))))))
              scheduled-future (.scheduleAtFixedRate
                                @scheduler
                                safe-task
                                initial-delay
                                period
                                TimeUnit/SECONDS)
              task-info {:id id
                         :schedule schedule
                         :schedule-info schedule-info
                         :description description
                         :future scheduled-future
                         :created-at (System/currentTimeMillis)}]

          (swap! scheduled-tasks assoc id task-info)

          {:status :ok
           :id id
           :schedule schedule
           :description description
           :initial-delay-seconds initial-delay
           :period-seconds period
           :text (str "Scheduled task '" id "' with expression: " schedule
                      "\nNext run in " initial-delay " seconds"
                      (when description (str "\nDescription: " description)))})))
    (catch Exception e
      {:status :error
       :message (.getMessage e)
       :exception (str e)})))

(defn cancel-task
  "Cancel a scheduled task.
   
   Args:
     :id - Task ID to cancel
   
   Returns:
     {:status :ok/:error}"
  [{:keys [id] :as _args}]
  (if-let [task-info (get @scheduled-tasks id)]
    (do
      (.cancel (:future task-info) false)
      (swap! scheduled-tasks dissoc id)
      {:status :ok
       :id id
       :text (str "Cancelled task: " id)})
    {:status :error
     :message (str "Task not found: " id)}))

(defn list-tasks
  "List all scheduled tasks.
   
   Returns:
     {:status :ok
      :tasks - Vector of task info maps}"
  [& _args]
  (let [tasks (vals @scheduled-tasks)
        task-summaries (map (fn [t]
                              {:id (:id t)
                               :schedule (:schedule t)
                               :description (:description t)
                               :created-at (:created-at t)
                               :cancelled? (.isCancelled (:future t))})
                            tasks)]
    {:status :ok
     :tasks task-summaries
     :count (count tasks)
     :text (if (empty? tasks)
             "No scheduled tasks"
             (str "Scheduled tasks:\n"
                  (str/join "\n"
                            (map (fn [t]
                                   (str "  - " (:id t)
                                        " [" (:schedule t) "]"
                                        (when (:description t)
                                          (str " - " (:description t)))))
                                 task-summaries))))}))

(defn run-once-after
  "Run a task once after a specified delay.
   
   Args:
     :id - Unique identifier for this task
     :delay - Delay in seconds before running
     :task - Function to execute (takes no args)
     :description - Optional description
   
   Returns:
     {:status :ok/:error}"
  [{:keys [id delay task description] :as _args}]
  (try
    (let [safe-task (fn []
                      (try
                        (task)
                        (finally
                          (swap! scheduled-tasks dissoc id))))
          scheduled-future (.schedule
                            @scheduler
                            safe-task
                            delay
                            TimeUnit/SECONDS)
          task-info {:id id
                     :type :once
                     :delay delay
                     :description description
                     :future scheduled-future
                     :created-at (System/currentTimeMillis)}]

      (swap! scheduled-tasks assoc id task-info)

      {:status :ok
       :id id
       :delay-seconds delay
       :description description
       :text (str "Scheduled one-time task '" id "' to run in " delay " seconds"
                  (when description (str "\nDescription: " description)))})
    (catch Exception e
      {:status :error
       :message (.getMessage e)
       :exception (str e)})))

(comment
  ;; Example usage

  ;; Schedule a task to run every 5 minutes
  (schedule-task
   {:id "backup-job"
    :schedule "*/5 * * * *"
    :task #(println "Running backup...")
    :description "Automated backup every 5 minutes"})

  ;; Schedule a daily task at midnight
  (schedule-task
   {:id "daily-report"
    :schedule "@daily"
    :task #(println "Generating daily report...")
    :description "Daily report generation"})

  ;; Run once after 30 seconds
  (run-once-after
   {:id "delayed-task"
    :delay 30
    :task #(println "Running delayed task...")
    :description "One-time delayed task"})

  ;; List all tasks
  (list-tasks)

  ;; Cancel a task
  (cancel-task {:id "backup-job"}))
