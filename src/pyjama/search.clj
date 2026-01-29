(ns pyjama.search
  "Search and browse analysis report history database.
   Provides interactive search capabilities for previously generated reports."
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [cheshire.core :as json]))

;; =============================================================================
;; ANSI Color Utilities
;; =============================================================================

(def ^:private colors
  "ANSI color codes for terminal output"
  {:red     "\033[0;31m"
   :green   "\033[0;32m"
   :yellow  "\033[1;33m"
   :blue    "\033[0;34m"
   :purple  "\033[0;35m"
   :cyan    "\033[0;36m"
   :white   "\033[1;37m"
   :gray    "\033[0;90m"
   :reset   "\033[0m"})

(defn- colorize
  "Apply color to text"
  [color text]
  (str (get colors color "") text (get colors :reset "")))

;; =============================================================================
;; Database Functions
;; =============================================================================

(def default-db-file
  "Default location for the report index database"
  (str (System/getProperty "user.home") "/.config/codebase-analyzer/index.jsonl"))

(defn- load-db-entries
  "Load all entries from the JSONL database file"
  [db-file]
  (when (.exists (io/file db-file))
    (try
      (let [lines (->> (slurp db-file)
                       (str/split-lines)
                       (remove str/blank?))
            parsed-entries (keep-indexed
                            (fn [idx line]
                              (try
                                (json/parse-string line true)
                                (catch Exception e
                                  (println (colorize :yellow
                                                     (str "⚠️  Skipping malformed line " (inc idx)
                                                          ": " (.getMessage e))))
                                  nil)))
                            lines)
            ;; Filter out entries missing required fields
            valid-entries (filter (fn [entry]
                                    (and (:generated_at entry)
                                         (:project entry)
                                         (:agent entry)))
                                  parsed-entries)]
        (->> valid-entries
             (sort-by :generated_at)
             reverse
             vec))
      (catch Exception e
        (println (colorize :red (str "Error loading database: " (.getMessage e))))
        []))))

(defn- format-entry-for-display
  "Format a database entry for display in the table"
  [entry]
  (let [date (-> (:generated_at entry "")
                 (str/replace "T" " ")
                 (str/replace "Z" "")
                 (subs 0 (min 16 (count (:generated_at entry "")))))
        project (-> (:project entry "")
                    (str/split #"/")
                    last
                    (or "unknown"))
        project-padded (format "%-20s" (if (> (count project) 20)
                                         (subs project 0 20)
                                         project))
        agent (:agent entry "unknown")
        duration (str (:duration_sec entry 0) "s")
        path (:report_path entry "")]
    {:date date
     :project project-padded
     :agent agent
     :duration duration
     :path path
     :display (str date "\t" project-padded "\t" agent "\t" duration "\t" path)}))

(defn- format-entries-for-fzf
  "Format all entries for FZF input"
  [entries]
  (->> entries
       (map format-entry-for-display)
       (map :display)
       (str/join "\n")))

;; =============================================================================
;; FZF Integration
;; =============================================================================

(defn- check-fzf-installed?
  "Check if fzf is installed and available"
  []
  (try
    (let [result (shell/sh "which" "fzf")]
      (zero? (:exit result)))
    (catch Exception _ false)))

(defn- run-fzf
  "Run FZF with the given input and options"
  [input-text]
  (try
    (let [result (shell/sh "fzf"
                           "--delimiter" "\t"
                           "--with-nth" "1,2,3,4"
                           "--header" "ENTER: View | SPACE: Open in App | P: Toggle Preview | ESC: Quit"
                           "--preview" "[ -f {5} ] && cat {5} || echo '❌ File not found: {5}'"
                           "--preview-window" "right:60%:wrap:hidden"
                           "--bind" "p:toggle-preview"
                           "--bind" "space:execute(open {5})"
                           "--bind" "enter:accept"
                           "--layout" "reverse"
                           "--height" "90%"
                           "--border"
                           :in input-text)]
      (when (zero? (:exit result))
        (str/trim (:out result))))
    (catch Exception e
      (println (colorize :red (str "Error running fzf: " (.getMessage e))))
      nil)))

(defn- extract-path-from-selection
  "Extract file path (5th column) from FZF selection"
  [selection]
  (when selection
    (-> selection
        (str/split #"\t")
        (nth 4 nil))))

(defn- open-file-in-pager
  "Open file in pager (less by default)"
  [file-path]
  (let [pager (or (System/getenv "PAGER") "less")]
    (try
      (.waitFor (.start (doto (ProcessBuilder. [pager file-path])
                          (.inheritIO))))
      true
      (catch Exception e
        (println (colorize :red (str "Error opening file: " (.getMessage e))))
        false))))

;; =============================================================================
;; Search Interface
;; =============================================================================

(defn search-reports
  "Interactive search of analysis report history.
   
   Options:
   - :db-file - Path to JSONL database (default: ~/.config/codebase-analyzer/index.jsonl)
   - :interactive? - Run in interactive loop mode (default: true)"
  [& {:keys [db-file interactive?]
      :or {db-file default-db-file
           interactive? true}}]

  ;; Check dependencies
  (when-not (check-fzf-installed?)
    (println (colorize :red "❌ Error: fzf is required. Please install it (brew install fzf)."))
    (System/exit 1))

  ;; Check database exists
  (when-not (.exists (io/file db-file))
    (println (colorize :red "❌ No report history found."))
    (println (str "Run some analyses first! DB stored at: " db-file))
    (System/exit 1))

  ;; Load entries
  (let [entries (load-db-entries db-file)]
    (when (empty? entries)
      (println (colorize :yellow "⚠️  Database exists but is empty/unreadable."))
      (System/exit 1))

    (println (colorize :cyan (str "🔎 Found " (count entries) " reports in history")))

    ;; Format for FZF
    (let [fzf-input (format-entries-for-fzf entries)]

      ;; Interactive loop
      (if interactive?
        (loop []
          (when-let [selection (run-fzf fzf-input)]
            (if-let [file-path (extract-path-from-selection selection)]
              (if (.exists (io/file file-path))
                (do
                  (open-file-in-pager file-path)
                  (recur)) ;; Loop back to list
                (do
                  (println (colorize :red (str "❌ Error: File not found at " file-path)))
                  (println "Press Enter to continue...")
                  (read-line)
                  (recur)))
              (do
                (println (colorize :yellow "⚠️  Could not extract file path from selection"))
                (recur)))))

        ;; Non-interactive: single selection
        (when-let [selection (run-fzf fzf-input)]
          (when-let [file-path (extract-path-from-selection selection)]
            (if (.exists (io/file file-path))
              (open-file-in-pager file-path)
              (println (colorize :red (str "❌ Error: File not found at " file-path)))))))))

  ;; Exit cleanly
  (println (colorize :gray "Search session ended."))
  nil)

(defn list-reports
  "List all reports in the database without interactive search.
   
   Options:
   - :db-file - Path to JSONL database
   - :format - Output format (:table or :json, default :table)"
  [& {:keys [db-file format]
      :or {db-file default-db-file
           format :table}}]

  (when-not (.exists (io/file db-file))
    (println (colorize :yellow "No report history found."))
    {:reports [] :total 0})

  (let [entries (load-db-entries db-file)]
    (if (= format :json)
      ;; JSON output
      {:reports entries
       :total (count entries)}

      ;; Table output
      (do
        (println)
        (println (colorize :cyan (str "📊 Report History (" (count entries) " reports)")))
        (println)
        (println (colorize :white "DATE/TIME       PROJECT             AGENT                    DURATION  PATH"))
        (println (colorize :gray "────────────────────────────────────────────────────────────────────────────────────────────────────"))

        (doseq [entry entries]
          (let [formatted (format-entry-for-display entry)]
            (println (:display formatted))))

        (println)
        {:reports entries
         :total (count entries)}))))

(defn get-report-stats
  "Get statistics about reports in the database.
   
   Options:
   - :db-file - Path to JSONL database"
  [& {:keys [db-file]
      :or {db-file default-db-file}}]

  (if-not (.exists (io/file db-file))
    {:total 0
     :by-agent {}
     :by-project {}
     :avg-duration 0}

    (let [entries (load-db-entries db-file)
          by-agent (frequencies (map :agent entries))
          by-project (frequencies (map #(-> % :project (str/split #"/") last) entries))
          total-duration (reduce + (map :duration_sec entries))
          avg-duration (if (pos? (count entries))
                         (/ total-duration (count entries))
                         0)]
      {:total (count entries)
       :by-agent by-agent
       :by-project by-project
       :avg-duration (format "%.1f" (double avg-duration))
       :total-duration total-duration})))
