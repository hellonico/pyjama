(ns pyjama.tools.cron-shell-integration-test
  "Integration test demonstrating cron and shell tools working together"
  (:require [clojure.test :refer [deftest is testing]]
            [pyjama.tools.cron :as cron]
            [pyjama.tools.shell :as shell]))

(deftest test-scheduled-date-printing
  (testing "Schedule tasks to print date every second for 5 seconds"
    (let [execution-count (atom 0)
          outputs (atom [])]

      ;; Schedule 5 tasks, one per second
      (doseq [i (range 5)]
        (cron/run-once-after
         {:id (str "date-task-" i)
          :delay (inc i) ; 1, 2, 3, 4, 5 seconds
          :task (fn []
                  (let [result (shell/execute-command {:command "date"})]
                    (swap! execution-count inc)
                    (swap! outputs conj {:iteration i
                                         :timestamp (System/currentTimeMillis)
                                         :output (:out result)
                                         :status (:status result)})
                    (println (str "Task " i " executed at: " (:out result)))))
          :description (str "Print date at second " (inc i))}))

      ;; Wait for all tasks to complete (6 seconds to be safe)
      (Thread/sleep 6000)

      ;; Verify all tasks executed
      (is (= 5 @execution-count) "All 5 tasks should have executed")

      ;; Verify all tasks succeeded
      (is (every? #(= :ok (:status %)) @outputs) "All date commands should succeed")

      ;; Verify all outputs contain date information
      (is (every? #(seq (:output %)) @outputs) "All tasks should produce output")

      ;; Print summary
      (println "\n=== Execution Summary ===")
      (doseq [{:keys [iteration output]} @outputs]
        (println (str "Iteration " iteration ": " (clojure.string/trim output))))
      (println "========================\n"))))

(deftest test-recurring-shell-commands
  (testing "Schedule a recurring task that runs shell commands"
    (let [execution-count (atom 0)]

      ;; Schedule a task to run every 2 seconds (using run-once-after in a loop simulation)
      (doseq [i (range 3)] ; 3 executions over 6 seconds
        (cron/run-once-after
         {:id (str "recurring-task-" i)
          :delay (* (inc i) 2) ; 2, 4, 6 seconds
          :task (fn []
                  (swap! execution-count inc)
                  (let [date-result (shell/execute-command {:command "date"})
                        echo-result (shell/execute-command {:command "echo 'Hello from cron task!'"})]
                    (println (str "\n--- Execution " i " ---"))
                    (println "Date:" (clojure.string/trim (:out date-result)))
                    (println "Message:" (clojure.string/trim (:out echo-result)))))
          :description (str "Recurring task iteration " i)}))

      ;; Wait for all tasks
      (Thread/sleep 7000)

      ;; Verify executions
      (is (= 3 @execution-count) "All 3 recurring tasks should have executed"))))

(comment
  ;; Manual test - run this in a REPL
  (do
    (println "Starting scheduled date printing test...")
    (println "Watch for date outputs every second for 5 seconds:\n")

    ;; Schedule date printing every second
    (doseq [i (range 5)]
      (cron/run-once-after
       {:id (str "manual-date-" i)
        :delay (inc i)
        :task (fn []
                (let [result (shell/execute-command {:command "date"})]
                  (println (str "[Second " (inc i) "] " (:out result)))))
        :description (str "Manual test - second " (inc i))}))

    (println "Tasks scheduled. Waiting for completion...\n")
    (Thread/sleep 6000)
    (println "\nTest complete!"))

  ;; List all scheduled tasks
  (cron/list-tasks))
