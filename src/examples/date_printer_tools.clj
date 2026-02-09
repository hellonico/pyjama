(ns examples.date-printer-tools
  "Helper tools for date printer agent")

(defn create-iterations
  "Create a list of 5 iterations for the loop"
  [_]
  {:items [1 2 3 4 5]})

(defn print-results
  "Print the loop results and observations"
  [{:keys [ctx]}]
  (println "\n╔════════════════════════════════════════╗")
  (println "║  Date Printer Results                  ║")
  (println "╚════════════════════════════════════════╝\n")

  (let [results (get-in ctx [:last-obs :loop-results])
        total (get-in ctx [:last-obs :loop-count])]

    (println (str "Total iterations: " total "\n"))

    (doseq [[idx result] (map-indexed vector results)]
      (let [;; Extract the date from the command field
            cmd (:command result)
            ;; The date is after "echo [Iteration X of Y]" in the command
            date-match (when cmd (re-find #"(\w{3} \w{3} \d+ \d{2}:\d{2}:\d{2} \w+ \d{4})" cmd))
            date-str (if date-match (second date-match) "N/A")]
        (println (str "  [" (inc idx) "] " date-str))))

    (println "\n✓ All iterations completed successfully!")
    (println (str "  Total time: ~" (* total 1) " seconds\n")))

  {:status :ok :message "Results printed"})
