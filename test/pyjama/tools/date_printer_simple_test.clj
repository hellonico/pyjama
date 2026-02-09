(ns date-printer-simple-test
  "Test agent demonstrating cron + shell integration"
  (:require [pyjama.tools.cron :as cron]
            [pyjama.tools.shell :as shell]))

(defn run-date-task [i]
  "Task function that runs the date command"
  (let [result (shell/execute-command {:command "date"})
        output (clojure.string/trim (:out result))]
    (println (str "[Second " (inc i) "] " output))))

(defn -main []
  (println "\n╔══════════════════════════════════════════╗")
  (println "║  Date Printer Test - Cron + Shell       ║")
  (println "╚══════════════════════════════════════════╝\n")
  (println "Scheduling 5 date printing tasks...\n")

  ;; Schedule 5 tasks
  (doseq [i (range 5)]
    (cron/run-once-after
     {:id (str "date-" i)
      :delay (inc i)
      :task #(run-date-task i)
      :description (str "Print date at second " (inc i))}))

  (println "✓ All tasks scheduled\n")
  (println "Waiting for execution...\n")

  ;; Wait for completion
  (Thread/sleep 6000)

  (println "\n╔════════════════════════════════╗")
  (println "║  Summary                       ║")
  (println "╚════════════════════════════════╝")
  (let [tasks (cron/list-tasks)]
    (println (str "Remaining tasks: " (:count tasks))))
  (println "\nTest Complete! 🎉\n"))

(comment
  (-main))
