#!/usr/bin/env clojure

(require '[pyjama.tools.cron :as cron])
(require '[pyjama.tools.shell :as shell])

(println "\n╔════════════════════════════════════════════════╗")
(println "║  Cron + Shell Integration Test               ║")
(println "║  Printing date every second for 5 seconds    ║")
(println "╚════════════════════════════════════════════════╝\n")

;; Schedule date printing every second for 5 seconds
(doseq [i (range 5)]
  (cron/run-once-after
   {:id (str "date-task-" i)
    :delay (inc i) ; Delay in seconds: 1, 2, 3, 4, 5
    :task (fn []
            (let [result (shell/execute-command {:command "date"})
                  timestamp (clojure.string/trim (:out result))]
              (println (format "│ [Task %d] %s" i timestamp))))
    :description (str "Date task #" i)}))

(println "⏱  Tasks scheduled. Waiting for execution...\n")

;; Wait for all tasks to complete (6 seconds to be safe)
(Thread/sleep 6000)

(println "\n✓ All tasks completed!\n")

;; Show remaining tasks (should be 0 as one-time tasks auto-cleanup)
(let [tasks-result (cron/list-tasks)]
  (println (format "ℹ  Remaining scheduled tasks: %d" (:count tasks-result))))

(println "\n════════════════════════════════════════════════\n")
