(ns cron-shell-demo
  "Demo script showing cron scheduling shell commands every second"
  (:require [pyjama.tools.cron :as cron]
            [pyjama.tools.shell :as shell]))

(defn -main []
  (println "=== Cron + Shell Integration Demo ===")
  (println "Scheduling date command to run every second for 5 seconds...\n")

  ;; Schedule 5 tasks, one per second
  (doseq [i (range 5)]
    (cron/run-once-after
     {:id (str "date-" i)
      :delay (inc i) ; 1, 2, 3, 4, 5 seconds
      :task (fn []
              (let [result (shell/execute-command {:command "date"})]
                (println (str "[Second " (inc i) "] "
                              (clojure.string/trim (:out result))))))
      :description (str "Print date at second " (inc i))}))

  (println "Tasks scheduled. Waiting for execution...\n")

  ;; Wait for all tasks to complete
  (Thread/sleep 6000)

  (println "\n=== Demo Complete ===")

  ;; Show final task list (should be empty as one-time tasks auto-cleanup)
  (let [tasks (cron/list-tasks)]
    (println (str "Remaining tasks: " (:count tasks)))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))

(comment
  ;; Run this in a REPL
  (-main))
