(ns pyjama.cli.search
  "CLI interface for pyjama report search"
  (:require
   [clojure.string :as str]
   [pyjama.search :as search]
   [cheshire.core :as json])
  (:gen-class))

(defn run-search
  "Run interactive report search"
  [& args]
  (let [db-file (or (first args) search/default-db-file)]
    (search/search-reports :db-file db-file :interactive? true)))

(defn run-list
  "List all reports"
  [& args]
  (let [json? (some #{"--json"} args)
        db-file (or (some #(when (not (str/starts-with? % "-")) %) args)
                    search/default-db-file)
        result (search/list-reports
                :db-file db-file
                :format (if json? :json :table))]

    (when json?
      (println (json/generate-string result {:pretty true})))))

(defn run-stats
  "Show report statistics"
  [& args]
  (let [db-file (or (first args) search/default-db-file)
        stats (search/get-report-stats :db-file db-file)]

    (println)
    (println "📊 Report Database Statistics")
    (println "═══════════════════════════════════════════════")
    (println)
    (printf "  Total Reports:        %d\n" (:total stats))
    (printf "  Average Duration:     %s seconds\n" (:avg-duration stats))
    (printf "  Total Time Spent:     %d seconds\n" (:total-duration stats))
    (println)

    (when (seq (:by-agent stats))
      (println "  Reports by Agent:")
      (doseq [[agent count] (sort-by val > (:by-agent stats))]
        (printf "    %-30s %d\n" agent count))
      (println))

    (when (seq (:by-project stats))
      (println "  Reports by Project:")
      (doseq [[project count] (sort-by val > (:by-project stats))]
        (printf "    %-30s %d\n" project count))
      (println))))

(defn print-help
  []
  (println "
Pyjama Report Search Tool
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Search and browse analysis report history database.

Usage: clj -M:search [command] [options]

Commands:
  search           Interactive search with FZF (default)
  list             List all reports in table format
  stats            Show report statistics

Options:
  --json           Output in JSON format (for list command)
  [db-file]        Specify custom database file path

Database Location:
  Default: ~/.config/codebase-analyzer/index.jsonl

Requirements:
  - fzf (for interactive search)
    Install: brew install fzf

Examples:
  clj -M:search                              # Interactive search (default)
  clj -M:search search                       # Interactive search (explicit)
  clj -M:search list                         # List all reports
  clj -M:search list --json                  # List in JSON format
  clj -M:search stats                        # Show statistics
  clj -M:search search /custom/path/db.jsonl # Custom database

Interactive Mode Keys:
  ENTER       View report in pager
  SPACE       Open report in default app (e.g., Memoa for .md)
  P           Toggle preview pane
  ESC         Quit search

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"))

(defn -main
  "Main entry point for search CLI"
  [& args]
  (try
    (if (empty? args)
      ;; Default to interactive search
      (run-search)

      (let [[command & params] args]
        (case command
          "search" (apply run-search params)
          "list" (apply run-list params)
          "stats" (apply run-stats params)
          "help" (print-help)
          "--help" (print-help)
          "-h" (print-help)

          ;; Unknown command - show help
          (do
            (println "❌ Unknown command:" command)
            (print-help)
            (System/exit 1)))))

    (catch Exception e
      (println "❌ Error:" (.getMessage e))
      (when (System/getProperty "debug")
        (.printStackTrace e))
      (System/exit 1)))

  (System/exit 0))
