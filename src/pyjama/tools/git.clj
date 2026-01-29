(ns pyjama.tools.git
  "Git analysis tools for discovering patterns, history, and PR insights"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.java.shell :as shell])
  (:import (java.io File)))

;; =============================================================================
;; Git History & Commits
;; =============================================================================

(defn parse-git-log-numstat
  "Parse git log with numstat to get file changes"
  [project-dir]
  (let [result (shell/sh "git" "log" "--numstat" "--pretty=format:%H|%an|%ae|%ad|%s"
                         "--date=iso" :dir project-dir)]
    (when (zero? (:exit result))
      (let [lines (str/split-lines (:out result))
            commits (atom [])]
        (loop [remaining-lines lines
               current-commit nil]
          (if (empty? remaining-lines)
            ;; End of lines - add last commit if exists
            (do
              (when current-commit
                (swap! commits conj current-commit))
              @commits)
            ;; Process next line
            (let [line (first remaining-lines)]
              (cond
                ;; Commit header line (contains |)
                (str/includes? line "|")
                (let [[hash author email date & msg-parts] (str/split line #"\|")
                      msg (str/join "|" msg-parts)]
                  ;; Save previous commit before starting new one
                  (when current-commit
                    (swap! commits conj current-commit))
                  (recur (rest remaining-lines)
                         {:hash hash
                          :author author
                          :email email
                          :date date
                          :message msg
                          :files []}))

                ;; File change line (added deleted filename)
                (and (not (str/blank? line))
                     (re-matches #"\d+\s+\d+\s+.+" line))
                (let [[added deleted file] (str/split line #"\s+" 3)
                      added-int (try (Integer/parseInt added) (catch Exception _ 0))
                      deleted-int (try (Integer/parseInt deleted) (catch Exception _ 0))]
                  (recur (rest remaining-lines)
                         (update current-commit :files conj
                                 {:file file
                                  :added added-int
                                  :deleted deleted-int
                                  :total (+ added-int deleted-int)})))

                ;; Empty line or other - skip
                :else
                (recur (rest remaining-lines) current-commit)))))))))

(defn calculate-file-hotspots
  "Calculate file hotspots based on change frequency and size"
  [commits]
  (let [file-stats (atom {})]
    (doseq [commit commits
            file-change (:files commit)]
      (let [file (:file file-change)
            total (:total file-change)]
        (swap! file-stats update file
               (fn [stats]
                 (-> (or stats {:changes 0 :total-lines 0 :commits #{}})
                     (update :changes inc)
                     (update :total-lines + total)
                     (update :commits conj (:hash commit)))))))

    ;; Calculate risk score
    (->> @file-stats
         (map (fn [[file stats]]
                (let [avg-size (/ (:total-lines stats) (:changes stats))
                      risk-score (* (:changes stats) avg-size)]
                  {:file file
                   :changes (:changes stats)
                   :total-lines (:total-lines stats)
                   :avg-change-size avg-size
                   :risk-score risk-score})))
         (sort-by :risk-score >)
         vec)))

(defn calculate-bus-factor
  "Calculate bus factor by file"
  [commits]
  (let [file-authors (atom {})]
    (doseq [commit commits
            file-change (:files commit)]
      (let [file (:file file-change)
            author (:author commit)
            lines (:total file-change)]
        (swap! file-authors update file
               (fn [authors]
                 (update (or authors {}) author
                         (fn [stats]
                           (-> (or stats {:commits 0 :lines 0})
                               (update :commits inc)
                               (update :lines + lines))))))))

    (->> @file-authors
         (map (fn [[file authors]]
                (let [total-lines (reduce + (map (comp :lines val) authors))
                      sorted-authors (sort-by (comp :lines val) > authors)
                      primary-author (first sorted-authors)
                      primary-pct (if (pos? total-lines)
                                    (* 100.0 (/ (-> primary-author val :lines) total-lines))
                                    0)]
                  {:file file
                   :total-authors (count authors)
                   :primary-author (key primary-author)
                   :primary-percentage primary-pct
                   :bus-factor (if (> primary-pct 75) 1 (count authors))})))
         (sort-by :bus-factor)
         vec)))

(defn build-cochange-graph
  "Build graph of files that change together"
  [commits]
  (let [cochange-pairs (atom {})]
    (doseq [commit commits]
      (let [files (map :file (:files commit))]
        (doseq [f1 files
                f2 files
                :when (not= f1 f2)]
          (let [pair (if (neg? (compare f1 f2)) [f1 f2] [f2 f1])]
            (swap! cochange-pairs update pair (fnil inc 0))))))

    (->> @cochange-pairs
         (map (fn [[[f1 f2] cnt]]
                {:file1 f1
                 :file2 f2
                 :cochange-count cnt}))
         (sort-by :cochange-count >)
         vec)))

(defn analyze-test-coverage
  "Analyze test-to-code change ratio"
  [commits]
  (let [stats (atom {:code-changes 0
                     :test-changes 0
                     :risky-commits []})]
    (doseq [commit commits]
      (let [files (:files commit)
            test-files (filter #(re-find #"(test|spec|_test\.|\. test\.)" (:file %)) files)
            code-files (remove #(re-find #"(test|spec|_test\.|\\.test\.)" (:file %)) files)
            code-lines (reduce + (map :total code-files))
            test-lines (reduce + (map :total test-files))]

        (swap! stats update :code-changes + code-lines)
        (swap! stats update :test-changes + test-lines)

        (when (and (pos? code-lines) (zero? test-lines))
          (swap! stats update :risky-commits conj
                 {:hash (:hash commit)
                  :message (:message commit)
                  :code-lines code-lines}))))

    (let [result @stats
          ratio (if (pos? (:code-changes result))
                  (/ (:test-changes result) (:code-changes result))
                  0)]
      (assoc result :test-to-code-ratio ratio))))

(defn identify-bug-fixes
  "Identify bug fix commits"
  [commits]
  (let [bug-keywords #"(?i)(fix|bug|issue|error|crash|regression|patch|hotfix)"]
    (->> commits
         (filter #(re-find bug-keywords (:message %)))
         vec)))

(defn format-deep-git-analysis
  "Format deep git analysis results into a readable summary"
  [data]
  (let [{:keys [commits-analyzed hotspots bus-factor cochange-graph test-analysis bug-fixes]} data]
    (str "# Deep Git Analysis Report\n\n"
         "**Commits Analyzed:** " commits-analyzed "\n\n"

         "## 1. Hotspots (High Churn & Size)\n"
         "Files with high risk scores based on frequency of change and average change size.\n"
         (str/join "\n" (for [h (take 10 hotspots)]
                          (format "- **%s**: Risk Score %.2f (%d changes, avg size %.1f lines)"
                                  (:file h) (double (:risk-score h)) (:changes h) (double (:avg-change-size h)))))
         "\n\n"

         "## 2. Bus Factor & Ownership\n"
         "Files where a single author has a high percentage of contribution.\n"
         (str/join "\n" (for [b (take 10 bus-factor)]
                          (format "- **%s**: Bus Factor %d (Primary: %s, %.1f%%)"
                                  (:file b) (:bus-factor b) (:primary-author b) (double (:primary-percentage b)))))
         "\n\n"

         "## 3. Co-change Patterns\n"
         "Files that frequently change together (indicates potential coupling).\n"
         (str/join "\n" (for [c (take 10 cochange-graph)]
                          (format "- %s <-> %s (%d co-changes)"
                                  (:file1 c) (:file2 c) (:cochange-count c))))
         "\n\n"

         "## 4. Test Coverage Trends\n"
         "Ratio of test code changes to production code changes.\n"
         "- **Test-to-Code Ratio:** " (format "%.3f" (double (:test-to-code-ratio test-analysis))) "\n"
         "- **Code Changes (lines):** " (:code-changes test-analysis) "\n"
         "- **Test Changes (lines):** " (:test-changes test-analysis) "\n"
         "- **Risky Commits (No Tests):** " (count (:risky-commits test-analysis)) "\n\n"

         "## 5. Recent Bug Fixes\n"
         (str/join "\n" (for [b (take 10 bug-fixes)]
                          (format "- [%s] %s" (subs (or (:hash b) "unknown") 0 7) (:message b))))
         "\n")))

(defn get-recent-diffs-for-files
  "Get the most recent diff for a set of files"
  [project-dir files]
  (if (empty? files)
    "No files specified for diff analysis."
    (str/join "\n\n"
              (for [file files]
                (let [result (shell/sh "git" "log" "-p" "-n" "1" "--" file :dir project-dir)]
                  (str "### Recent Diffs for " file "\n"
                       "```diff\n"
                       (if (zero? (:exit result))
                         (let [out (:out result)]
                           (if (> (count out) 5000)
                             (str (subs out 0 5000) "\n... (truncated)")
                             (if (str/blank? out) "No recent changes found in log." out)))
                         (str "Failed to fetch diffs: " (:err result)))
                       "\n```"))))))

(defn deep-git-analysis
  "Perform comprehensive deep git analysis"
  [{:keys [dir limit]
    :or {limit 500}}]
  (let [project-dir (or dir ".")
        commits (take limit (parse-git-log-numstat project-dir))
        hotspots (take 20 (calculate-file-hotspots commits))
        bus-factor (take 20 (calculate-bus-factor commits))
        cochange-graph (take 30 (build-cochange-graph commits))
        test-analysis (analyze-test-coverage commits)
        bug-fixes (take 20 (identify-bug-fixes commits))

        ;; Get diffs for top 3 hotspots
        top-hotspot-files (take 3 (map :file hotspots))
        hotspot-diffs (get-recent-diffs-for-files project-dir top-hotspot-files)

        result-data {:status :ok
                     :commits-analyzed (count commits)
                     :hotspots hotspots
                     :bus-factor bus-factor
                     :cochange-graph cochange-graph
                     :test-analysis test-analysis
                     :bug-fixes bug-fixes}]

    (assoc result-data :text (str (format-deep-git-analysis result-data)
                                  "\n## 6. Hotspot Code Diffs\n"
                                  "Recent changes in top hotspot files for context.\n\n"
                                  hotspot-diffs))))

;; =============================================================================
;; PR Review Analysis
;; =============================================================================

(defn get-merge-base
  "Get merge base between two branches"
  [project-dir base-branch head-branch]
  (let [result (shell/sh "git" "merge-base" base-branch head-branch :dir project-dir)]
    (when (zero? (:exit result))
      (str/trim (:out result)))))

(defn get-pr-commits
  "Get list of commits in the PR"
  [project-dir base-branch head-branch]
  (let [merge-base (get-merge-base project-dir base-branch head-branch)
        result (shell/sh "git" "log" "--pretty=format:%H|%an|%ae|%ad|%s"
                         "--date=iso" (str merge-base ".." head-branch)
                         :dir project-dir)]
    (when (zero? (:exit result))
      (->> (str/split-lines (:out result))
           (map (fn [line]
                  (let [[hash author email date & msg-parts] (str/split line #"\|")]
                    {:hash hash
                     :author author
                     :email email
                     :date date
                     :message (str/join "|" msg-parts)})))
           vec))))

(defn get-changed-files
  "Get list of changed files with stats"
  [project-dir base-branch head-branch]
  (let [merge-base (get-merge-base project-dir base-branch head-branch)
        result (shell/sh "git" "diff" "--numstat" (str merge-base ".." head-branch)
                         :dir project-dir)]
    (when (zero? (:exit result))
      (->> (str/split-lines (:out result))
           (remove str/blank?)
           (map (fn [line]
                  (let [[added deleted file] (str/split line #"\s+" 3)
                        added-int (try (Integer/parseInt added) (catch Exception _ 0))
                        deleted-int (try (Integer/parseInt deleted) (catch Exception _ 0))]
                    {:file file
                     :added added-int
                     :deleted deleted-int
                     :total (+ added-int deleted-int)})))
           vec))))

(defn get-file-diff
  "Get actual diff content for a file"
  [project-dir base-branch head-branch file]
  (let [merge-base (get-merge-base project-dir base-branch head-branch)
        result (shell/sh "git" "diff" (str merge-base ".." head-branch) "--" file
                         :dir project-dir)]
    (when (zero? (:exit result))
      (:out result))))

(defn classify-file-type
  "Classify file by extension and path"
  [file-path]
  (let [ext (last (str/split file-path #"\."))
        path-lower (str/lower-case file-path)]
    (cond
      ;; Tests
      (re-find #"(test|spec|_test\.|\.test\.)" path-lower) :test

      ;; Config/Build
      (re-find #"(package\.json|deps\.edn|pom\.xml|Cargo\.toml|requirements\.txt|Gemfile|\.yml|\.yaml|Dockerfile|Makefile)" file-path) :config

      ;; Documentation
      (re-find #"(\.md|\.txt|README|CHANGELOG|LICENSE|\.adoc)" file-path) :documentation

      ;; Infrastructure
      (re-find #"(\.tf|\.hcl|k8s|kubernetes|helm|ansible)" path-lower) :infrastructure

      ;; Database
      (re-find #"(migration|schema|\.sql)" path-lower) :database

      ;; Source code
      (#{"clj" "cljs" "cljc" "java" "js" "ts" "tsx" "jsx" "py" "rb" "go" "rs" "kt"} ext) :source

      :else :other)))

(defn detect-sensitive-areas
  "Detect if file is in sensitive area"
  [file-path diff-content]
  (let [path-lower (str/lower-case file-path)
        sensitive-patterns
        {:auth (re-find #"(auth|login|session|token|jwt|oauth|permission|role)" path-lower)
         :security (re-find #"(security|crypto|encrypt|hash|password|secret)" path-lower)
         :payment (re-find #"(payment|billing|stripe|paypal|transaction)" path-lower)
         :data-access (re-find #"(database|db|sql|query|repository)" path-lower)
         :api (re-find #"(api|endpoint|route|controller)" path-lower)}

        content-patterns
        {:auth-logic (re-find #"(authenticate|authorize|checkPermission|verifyToken)" diff-content)
         :crypto (re-find #"(encrypt|decrypt|hash|sign|verify)" diff-content)
         :sql (re-find #"(SELECT|INSERT|UPDATE|DELETE|CREATE TABLE)" diff-content)
         :serialization (re-find #"(serialize|deserialize|JSON\.parse|eval)" diff-content)}]

    (merge
     (into {} (filter val sensitive-patterns))
     (into {} (filter val content-patterns)))))

(defn classify-change-semantics
  "Classify what kind of change this is"
  [diff-content]
  (let [has-logic-change (re-find #"[\+\-]\s*(if|else|for|while|switch|case|when|cond)" diff-content)
        has-return-change (re-find #"[\+\-]\s*return" diff-content)
        has-error-handling (re-find #"[\+\-]\s*(try|catch|throw|except|raise|error)" diff-content)
        has-async-change (re-find #"[\+\-]\s*(async|await|promise|future|go)" diff-content)
        has-import-change (re-find #"[\+\-]\s*(import|require|use|include|from)" diff-content)
        has-export-change (re-find #"[\+\-]\s*(export|public|defn|def |class )" diff-content)
        has-whitespace-only (and (re-find #"^[\+\-]\s*$" diff-content)
                                 (not has-logic-change))]

    (cond
      has-whitespace-only :formatting
      has-import-change :dependency
      has-export-change :api-change
      (or has-logic-change has-return-change) :behavior-change
      has-error-handling :error-handling
      has-async-change :concurrency
      :else :refactor)))

(defn calculate-pr-risk-score
  "Calculate overall PR risk score"
  [{:keys [files commits sensitive-files behavior-changes api-changes]}]
  (let [;; Size factors
        total-lines (reduce + (map :total files))
        file-count (count files)

        ;; Breadth (entropy)
        directories (count (distinct (map #(str/join "/" (butlast (str/split (:file %) #"/"))) files)))
        entropy (/ directories (max 1 file-count))

        ;; Sensitive areas
        sensitive-count (count sensitive-files)

        ;; Change types
        behavior-count (count behavior-changes)
        api-count (count api-changes)

        ;; Scoring
        size-score (min 30 (/ total-lines 10))
        breadth-score (min 20 (* entropy 20))
        sensitive-score (* sensitive-count 15)
        behavior-score (* behavior-count 10)
        api-score (* api-count 15)

        total-score (+ size-score breadth-score sensitive-score behavior-score api-score)]

    {:score total-score
     :level (cond
              (> total-score 70) :high
              (> total-score 35) :medium
              :else :low)
     :factors {:size size-score
               :breadth breadth-score
               :sensitive sensitive-score
               :behavior behavior-score
               :api api-score}
     :reasons (cond-> []
                (> size-score 20) (conj (format "Large change: %d lines across %d files" total-lines file-count))
                (> breadth-score 10) (conj (format "Wide breadth: changes span %d directories" directories))
                (pos? sensitive-count) (conj (format "%d sensitive files touched" sensitive-count))
                (pos? behavior-count) (conj (format "%d files with behavior changes" behavior-count))
                (pos? api-count) (conj (format "%d files with API changes" api-count)))}))

(defn get-file-contributors
  "Get top contributors for a file"
  [project-dir file limit]
  (let [result (shell/sh "git" "log" "--follow" "--pretty=format:%an|%ae"
                         "--" file :dir project-dir)]
    (when (zero? (:exit result))
      (let [authors (->> (str/split-lines (:out result))
                         (map #(str/split % #"\|"))
                         (map (fn [[name email]] {:name name :email email}))
                         frequencies
                         (sort-by val >)
                         (take limit)
                         (map (fn [[author count]] (assoc author :commits count))))]
        (vec authors)))))

(defn suggest-reviewers
  "Suggest reviewers based on file ownership"
  [project-dir changed-files]
  (let [file-experts (for [file changed-files]
                       {:file (:file file)
                        :experts (get-file-contributors project-dir (:file file) 3)})]
    (->> file-experts
         (mapcat :experts)
         (group-by :email)
         (map (fn [[email authors]]
                (let [author (first authors)
                      total-commits (reduce + (map :commits authors))]
                  (assoc author :total-commits total-commits))))
         (sort-by :total-commits >)
         (take 5)
         vec)))

(defn map-tests-to-code
  "Map test files to their corresponding source files"
  [test-files source-files]
  (for [test-file test-files]
    (let [test-path (:file test-file)
          ;; Try to find matching source file
          potential-sources
          (for [src source-files]
            (let [src-path (:file src)
                  src-name (last (str/split src-path #"/"))
                  test-name (last (str/split test-path #"/"))
                  ;; Remove test suffixes
                  clean-test (-> test-name
                                 (str/replace #"_test\." ".")
                                 (str/replace #"\.test\." ".")
                                 (str/replace #"_spec\." ".")
                                 (str/replace #"\.spec\." ".")
                                 (str/replace #"Test\." ".")
                                 (str/replace #"Spec\." "."))]
              (when (or (= src-name clean-test)
                        (str/includes? test-name (str/replace src-name #"\.\w+$" "")))
                {:source src-path
                 :test test-path
                 :source-changes (:total src)
                 :test-changes (:total test-file)})))]
      {:test test-path
       :test-changes (:total test-file)
       :likely-sources (vec (remove nil? potential-sources))})))

(defn get-pr-info
  [project-dir base-branch head-branch]
  (let [result (shell/sh "git" "log" "-1" "--pretty=format:%s|%an" head-branch :dir project-dir)]
    (if (zero? (:exit result))
      (let [[title author] (str/split (str/trim (:out result)) #"\|")]
        {:title title :author author})
      {:title "Unknown" :author "Unknown"})))

(defn get-files-diff-summary
  [project-dir base-branch head-branch files]
  (str/join "\n\n"
            (for [file (take 10 files)]
              (let [diff (shell/sh "git" "diff" (str base-branch ".." head-branch) "--" (:file file) :dir project-dir)]
                (str "File: " (:file file) "\n"
                     "```diff\n"
                     (subs (:out diff) 0 (min 3000 (count (:out diff))))
                     "\n```")))))

(defn format-analyze-pr
  "Format PR analysis results into a readable summary"
  [data]
  (let [{:keys [summary risk sensitive-files behavior-changes api-changes test-gap diff-context]} data]
    (str "# PR Analysis: " (:title summary) "\n"
         "**Author:** " (:author summary) "\n"
         "**Base:** " (:base-branch summary) " | **Head:** " (:head-branch summary) "\n"
         "**Stats:** " (:commits summary) " commits, " (:files summary) " files changed, " (:total-lines summary) " lines affected.\n\n"

         "## Risk Assessment: " (str/upper-case (name (or (:level risk) :low))) " (Score: " (or (:score risk) 0) ")\n"
         (str/join "\n" (map #(str "- " %) (:reasons risk)))
         "\n\n"

         (when (seq sensitive-files)
           (str "### ⚠️ Sensitive Files Touched\n"
                (str/join "\n" (for [f sensitive-files]
                                 (str "- " (:file f) " (detected: " (str/join ", " (keys (:sensitive f))) ")")))
                "\n\n"))

         (when (seq behavior-changes)
           (str "### ⚙️ Behavior Changes\n"
                (str/join "\n" (for [f behavior-changes] (str "- " (:file f))))
                "\n\n"))

         (when (seq api-changes)
           (str "### 🔌 API Changes\n"
                (str/join "\n" (for [f api-changes] (str "- " (:file f))))
                "\n\n"))

         (when (:has-gap test-gap)
           (str "### 🧪 Test Gap Detected\n"
                "Source files were changed but no corresponding test changes were found.\n\n"))

         "## Diff Context\n"
         diff-context)))

(defn analyze-pr
  "Perform comprehensive PR analysis between two branches"
  [{:keys [dir base-branch head-branch]
    :or {base-branch "main"}}]

  (let [commits (get-pr-commits dir base-branch head-branch)
        files (get-changed-files dir base-branch head-branch)

        ;; Classify files
        classified-files
        (for [file files]
          (let [diff (get-file-diff dir base-branch head-branch (:file file))
                file-type (classify-file-type (:file file))
                sensitive (detect-sensitive-areas (:file file) diff)
                semantics (classify-change-semantics diff)]
            (assoc file
                   :type file-type
                   :sensitive sensitive
                   :change-type semantics
                   :diff-preview (subs diff 0 (min 500 (count diff))))))

        ;; Group by type
        by-type (group-by :type classified-files)
        by-change-type (group-by :change-type classified-files)

        ;; Identify key files
        sensitive-files (filter #(seq (:sensitive %)) classified-files)
        behavior-changes (filter #(= :behavior-change (:change-type %)) classified-files)
        api-changes (filter #(= :api-change (:change-type %)) classified-files)
        test-files (filter #(= :test (:type %)) classified-files)
        source-files (filter #(= :source (:type %)) classified-files)

        ;; PR Info and Diff Summary (depends on classified files)
        pr-info (get-pr-info dir base-branch head-branch)

        ;; Capture diffs for context
        priority-files (concat sensitive-files behavior-changes api-changes source-files files)
        unique-files (distinct priority-files)
        diff-context (get-files-diff-summary dir base-branch head-branch (take 12 unique-files))

        ;; Risk assessment
        risk (calculate-pr-risk-score
              {:files files
               :commits commits
               :sensitive-files sensitive-files
               :behavior-changes behavior-changes
               :api-changes api-changes})

        ;; Test gap detection
        has-source-changes (seq source-files)
        has-test-changes (seq test-files)
        test-gap (and has-source-changes (not has-test-changes))

        ;; Reviewer suggestions
        reviewers (suggest-reviewers dir files)
        ;; Test-to-code mapping
        test-mapping (map-tests-to-code test-files source-files)

        ;; Stats
        total-added (reduce + (map :added files))
        total-deleted (reduce + (map :deleted files))
        total-changed (+ total-added total-deleted)]

    {:status :ok
     :summary {:commits (count commits)
               :files (count files)
               :added total-added
               :deleted total-deleted
               :total-lines total-changed
               :base-branch base-branch
               :head-branch head-branch
               :title (:title pr-info)
               :author (:author pr-info)}

     :risk risk

     :files-by-type by-type
     :files-by-change by-change-type

     :sensitive-files sensitive-files
     :behavior-changes behavior-changes
     :api-changes api-changes

     :test-gap {:has-gap test-gap
                :test-mapping test-mapping
                :source-files (count source-files)
                :test-files (count test-files)}

     :diff-context diff-context

     :suggested-reviewers reviewers

     :review-order (concat
                    (take 5 (sort-by :total > sensitive-files))
                    (take 5 (sort-by :total > behavior-changes))
                    (take 5 (sort-by :total > api-changes)))

     :all-files classified-files
     :text (format-analyze-pr
            {:summary {:commits (count commits)
                       :files (count files)
                       :added total-added
                       :deleted total-deleted
                       :total-lines total-changed
                       :base-branch base-branch
                       :head-branch head-branch
                       :title (:title pr-info)
                       :author (:author pr-info)}
             :risk risk
             :sensitive-files sensitive-files
             :behavior-changes behavior-changes
             :api-changes api-changes
             :test-gap {:has-gap test-gap}
             :diff-context diff-context})}))

;; =============================================================================
;; Main Git Tools API
;; =============================================================================

(defn read-gitignore
  "Read and parse .gitignore files from current and parent directories, returning list of patterns.
   This mimics git's behavior of respecting .gitignore files in parent directories."
  [root-dir]
  (let [root-file (io/file root-dir)
        ;; Collect all .gitignore files from current dir up to parent dirs (max 5 levels up)
        gitignore-files (loop [current-dir root-file
                               files []
                               depth 0]
                          (if (and current-dir (.exists current-dir) (< depth 5))
                            (let [gitignore (io/file current-dir ".gitignore")
                                  parent (.getParentFile current-dir)]
                              (if (.exists gitignore)
                                (recur parent (conj files gitignore) (inc depth))
                                (recur parent files (inc depth))))
                            files))
        ;; Parse all gitignore files and merge patterns
        all-patterns (mapcat (fn [gitignore-file]
                               (when (.exists gitignore-file)
                                 (->> (slurp gitignore-file)
                                      str/split-lines
                                      (map str/trim)
                                      (remove str/blank?)
                                      (remove #(str/starts-with? % "#"))
                                      (map #(-> %
                                                (str/replace "**/" "")   ; Remove leading **/ (simplification)
                                                (str/replace "." "\\.")  ; Escape dots
                                                (str/replace "*" ".*")))))) ; Convert glob * to regex .*
                             gitignore-files)]
    (vec (distinct all-patterns))))

(defn discover-codebase-with-gitignore
  "Recursively discover all code files in a directory with metadata, respecting .gitignore"
  [{:keys [dir extensions exclude-patterns]
    :or {extensions ["clj" "cljc" "cljs" "edn" "md" "java" "js" "ts" "py" "rb"
                     "toml" "yaml" "yml" "json" "xml" "lock" "tf" "hcl"]
         exclude-patterns ["target" "node_modules" "\\.git" "out" "classes" "build" "dist" "coverage" "\\.next" "\\.nuxt" "vendor" "tmp"
                           "\\.bak$" "\\.swp$" "\\.old$" "\\.orig$" "\\.DS_Store$" "~$" "\\.bak\\."]}}]
  (let [root (io/file (or dir "."))
        gitignore-patterns (read-gitignore root)
        all-patterns (concat exclude-patterns (or gitignore-patterns []))
        exclude-re (when (seq all-patterns)
                     (re-pattern (str "(" (str/join "|" all-patterns) ")")))]
    (when-not (.exists root)
      (throw (ex-info "Directory not found" {:dir dir})))
    (let [root-path (str (.getAbsolutePath root) "/")
          files (->> (file-seq root)
                     (filter #(.isFile ^File %))
                     ;; First map to lightweight object to get relative path
                     (map (fn [^File f]
                            (let [path (.getAbsolutePath f)
                                  rel-path (str/replace path root-path "")]
                              {:file path
                               :file-obj f
                               :relative-path rel-path})))
                     ;; Filter using relative path
                     (remove (fn [f]
                               (when exclude-re
                                 (re-find exclude-re (:relative-path f)))))
                     ;; Filter by extension
                     (filter #(some (fn [ext] (str/ends-with? (:file %) (str "." ext)))
                                    extensions))
                     ;; Hydrate full object
                     (map (fn [f]
                            (let [file-obj (:file-obj f)]
                              (assoc f
                                     :name (.getName file-obj)
                                     :extension (last (str/split (.getName file-obj) #"\."))
                                     :size (.length file-obj)
                                     :lines (with-open [rdr (io/reader file-obj)]
                                              (count (line-seq rdr)))
                                     :content (slurp file-obj)))))
                     (map #(dissoc % :file-obj))
                     vec)]
      {:status :ok
       :total-files (count files)
       :total-lines (reduce + (map :lines files))
       :total-size (reduce + (map :size files))
       :files files
       :project-files files
       :text (format "Discovered %d files (%d lines, %d bytes)"
                     (count files)
                     (reduce + (map :lines files))
                     (reduce + (map :size files)))})))

(defn analyze-git-history
  "Analyze git repository history for insights"
  [{:keys [dir limit] :or {limit 500}}]
  (let [repo-dir (io/file (or dir "."))
        git-dir (io/file repo-dir ".git")]
    (if-not (.exists git-dir)
      {:status :error
       :text "Not a git repository"}
      (try
        (let [;; Get commit log with stats
              log-cmd ["git" "-C" (.getAbsolutePath repo-dir)
                       "log" (str "--max-count=" limit)
                       "--pretty=format:%H|%an|%ae|%ad|%s"
                       "--date=iso" "--numstat"]
              log-result (-> (ProcessBuilder. ^java.util.List log-cmd)
                             (.redirectErrorStream true)
                             (.start))
              log-output (slurp (.getInputStream log-result))

              ;; Get contributor stats
              authors-cmd ["git" "-C" (.getAbsolutePath repo-dir)
                           "shortlog" "-sn" "--all"]
              authors-result (-> (ProcessBuilder. ^java.util.List authors-cmd)
                                 (.redirectErrorStream true)
                                 (.start))
              authors-output (slurp (.getInputStream authors-result))

              ;; Get file change frequency
              files-cmd ["git" "-C" (.getAbsolutePath repo-dir)
                         "log" "--all" "--pretty=format:" "--name-only"]
              files-result (-> (ProcessBuilder. ^java.util.List files-cmd)
                               (.redirectErrorStream true)
                               (.start))
              files-output (slurp (.getInputStream files-result))

              ;; Parse commit data
              commits (->> (str/split log-output #"\n\n")
                           (take limit)
                           (map str/trim)
                           (remove str/blank?)
                           (map (fn [commit-block]
                                  (let [lines (str/split commit-block #"\n")
                                        [hash author email date & msg-parts] (str/split (first lines) #"\|")
                                        stats (rest lines)]
                                    {:hash hash
                                     :author author
                                     :email email
                                     :date date
                                     :message (str/join "|" msg-parts)
                                     :files-changed (count stats)}))))

              ;; Parse author stats
              authors (->> (str/split-lines authors-output)
                           (map str/trim)
                           (remove str/blank?)
                           (map (fn [line]
                                  (let [[commits author] (str/split line #"\t" 2)]
                                    {:commits (Integer/parseInt (str/trim commits))
                                     :author (str/trim author)})))
                           (sort-by :commits >))

              ;; Parse file change frequency
              file-changes (->> (str/split-lines files-output)
                                (map str/trim)
                                (remove str/blank?)
                                frequencies
                                (sort-by val >)
                                (take 50)
                                (map (fn [[file count]] {:file file :changes count})))

              text (str "## Git Statistics\n"
                        "- Total Commits Analyzed: " (count commits) "\n"
                        "- Total Contributors: " (count authors) "\n\n"
                        "### Top Contributors\n"
                        (str/join "\n" (map #(format "- %s: %d commits" (:author %) (:commits %))
                                            (take 15 authors)))
                        "\n\n### Most Changed Files (Hotspots)\n"
                        (str/join "\n" (map #(format "- %s (%d changes)" (:file %) (:changes %))
                                            (take 25 file-changes)))
                        "\n\n### Recent Commit History\n"
                        (str/join "\n" (map #(format "- [%s] %s: %s" (:date %) (:author %) (:message %))
                                            (take 50 commits))))]

          {:status :ok
           :total-commits (count commits)
           :total-authors (count authors)
           :commits commits
           :top-contributors (take 10 authors)
           :most-changed-files (take 20 file-changes)
           :text text})
        (catch Exception e
          {:status :error
           :text (str "Git analysis failed: " (.getMessage e))})))))

(defn git-commit-timeline
  "Generate commit timeline analysis"
  [{:keys [dir months] :or {months 12}}]
  (let [repo-dir (io/file (or dir "."))]
    (try
      (let [since-date (str months " months ago")
            cmd ["git" "-C" (.getAbsolutePath repo-dir)
                 "log" "--all"
                 (str "--since=\"" since-date "\"")
                 "--pretty=format:%ad|%an"
                 "--date=short"]
            result (-> (ProcessBuilder. ^java.util.List cmd)
                       (.redirectErrorStream true)
                       (.start))
            output (slurp (.getInputStream result))

            commits-by-date (->> (str/split-lines output)
                                 (map #(str/split % #"\|"))
                                 (group-by first)
                                 (map (fn [[date commits]]
                                        {:date date
                                         :count (count commits)
                                         :authors (set (map second commits))}))
                                 (sort-by :date))

            text (str "## Commit Timeline (Last " months " months)\n"
                      "Total active days: " (count commits-by-date) "\n\n"
                      (str/join "\n" (map #(format "- %s: %d commits (Authors: %s)"
                                                   (:date %)
                                                   (:count %)
                                                   (str/join ", " (:authors %)))
                                          commits-by-date)))]

        {:status :ok
         :timeline commits-by-date
         :text text})
      (catch Exception e
        {:status :error
         :text (str "Timeline analysis failed: " (.getMessage e))}))))

(defn git-file-churn
  "Find files with highest change frequency (hotspots)"
  [{:keys [dir limit] :or {limit 10}}]
  (let [repo-dir (io/file (or dir "."))]
    (try
      (let [cmd ["git" "-C" (.getAbsolutePath repo-dir)
                 "log" "--all" "--pretty=format:" "--name-only"]
            result (-> (ProcessBuilder. ^java.util.List cmd)
                       (.redirectErrorStream true)
                       (.start))
            output (slurp (.getInputStream result))

            ;; Count file occurrences
            file-counts (->> (str/split-lines output)
                             (remove str/blank?)
                             frequencies
                             (sort-by second >)
                             (take limit))

            ;; Format output
            text (str "## Git Hotspots (Top " limit " Most Changed Files)\n\n"
                      (str/join "\n" (map (fn [[file count]]
                                            (format "%4d changes: %s" count file))
                                          file-counts)))

            ;; Extract just filenames for read-files tool
            filenames (mapv first file-counts)]

        {:status :ok
         :hotspots file-counts
         :data filenames
         :text text})
      (catch Exception e
        {:status :error
         :text (str "Hotspot analysis failed: " (.getMessage e))}))))

(defn cat-version-files
  "Return the raw content of version-related configuration files, prioritized.
   Focuses on dependency manifests, build configs, and infrastructure files.
   Args: {:keys [files max-files] :or {max-files 100}}"
  [{:keys [files ctx max-files] :or {max-files 100}}]
  (let [files (or files (:project-files ctx) [])
        ;; Safety blacklist - aggressively reject artifact/cache directories
        blacklisted-dirs #{"node_modules" ".gradle" ".git" "target" "dist" "build" "out" "vendor" ".idea" ".vscode" "coverage"}

        blacklisted? (fn [file-path]
                       (let [parts (set (str/split file-path #"/"))]
                         (some blacklisted-dirs parts)))

        ;; Define priority patterns for version-related configuration files
        priority-patterns
        {:critical ["README.md" "README.txt" "README" "CONTRIBUTING.md"
                    "package.json" "Cargo.toml" "pom.xml" "build.gradle" "build.gradle.kts"
                    "gradle.properties" "requirements.txt" "Pipfile" "pyproject.toml"
                    "Gemfile" "go.mod" "composer.json" "mix.exs" "pubspec.yaml"
                    "Makefile" "Dockerfile" "Containerfile"]
         :high ["CHANGELOG.md" "tsconfig.json" "hardhat.config.ts" "hardhat.config.js"
                "vite.config.js" "vite.config.ts" "jest.config.js"
                "webpack.config.js" "rollup.config.js" "babel.config.js"
                "next.config.js" "nuxt.config.js" "remix.config.js"
                "angular.json" "ionic.config.json" "serverless.yml" "serverless.yaml"]
         :medium ["docker-compose.yml" "docker-compose.yaml"
                  "main.tf" "variables.tf" "outputs.tf" "versions.tf"
                  ".terraform.lock.hcl" "terragrunt.hcl"
                  "procfile" "Procfile" "cdk.json" "azure-pipelines.yml"
                  ".gitlab-ci.yml" ".travis.yml" "appspec.yml" "buildspec.yml"
                  "netlify.toml" "vercel.json" "fly.toml"
                  "Cargo.lock" "Gemfile.lock" "Pipfile.lock" "composer.lock" "go.sum"]
         :low [".nvmrc" ".ruby-version" ".python-version" ".tool-versions"
               "shadow-cljs.edn" "deps.edn" "project.clj"
               ".eslintrc" ".eslintrc.js" ".eslintrc.json"
               ".prettierrc" ".prettierrc.json" "truffle-config.js"]}

        ;; Function to calculate priority score for a file
        priority-score (fn [file-path]
                         (let [filename (last (str/split file-path #"/"))]
                           (cond
                             (some #(= filename %) (:critical priority-patterns)) 1000
                             (some #(= filename %) (:high priority-patterns)) 500
                             (some #(= filename %) (:medium priority-patterns)) 250
                             (some #(= filename %) (:low priority-patterns)) 100

                             ;; Dynamic patterns
                             (or (str/ends-with? file-path ".tf")
                                 (str/ends-with? file-path ".hcl")) 600      ; Terraform/HCL - HIGH PRIORITY
                             (str/ends-with? file-path ".tfvars") 400        ; Terraform vars - Medium-High
                             ;; Lock files - downgraded priority and stricter check
                             (and (str/ends-with? file-path ".lock")
                                  (not (blacklisted? file-path))) 50

                             (str/ends-with? file-path ".gradle") 150        ; Gradle
                             (str/ends-with? file-path ".csproj") 800        ; C#
                             (str/ends-with? file-path ".fsproj") 800        ; F#

                             ;; K8s / CI / Deployment
                             (and (or (str/ends-with? file-path ".yaml") (str/ends-with? file-path ".yml"))
                                  (or (str/includes? file-path "k8s")
                                      (str/includes? file-path "kubernetes")
                                      (str/includes? file-path "deploy")
                                      (str/includes? file-path "github/workflows"))) 150

                             :else 0)))

        ;; Sort files by priority score (highest first), then by path
        prioritized-files (->> files
                               (remove #(blacklisted? (:relative-path %)))
                               (map (fn [f]
                                      (assoc f :priority-score
                                             (priority-score (:relative-path f)))))
                               (filter #(> (:priority-score %) 0))
                               (sort-by (juxt #(- (:priority-score %)) :relative-path))
                               (take max-files))

        ;; Format output
        joined (->> prioritized-files
                    (map (fn [f]
                           (str "### File: " (:relative-path f)
                                " (priority: " (:priority-score f) ")\n"
                                "```" (:extension f) "\n"
                                (:content f) "\n"
                                "```\n\n")))
                    (apply str))

        summary (str "Found " (count prioritized-files) " version-related files "
                     "(from " (count files) " total files)\n\n")]
    {:status :ok
     :text (str summary joined)
     :files (mapv :relative-path prioritized-files)
     :total-version-files (count prioritized-files)}))

(defn load-git-history-template
  "Load the git history analysis template"
  [_args]
  (try
    (let [template (slurp (io/resource "analysis-templates/git_history_analysis.md"))]
      {:status :ok
       :template template
       :text template})
    (catch Exception e
      {:status :error
       :text (str "Failed to load template: " (.getMessage e))})))

(defn prepare-git-prompt-debug
  "Prepare the final prompt for git history analysis with debug output"
  [{:keys [template git-data timeline project-dir]}]
  (let [prompt (-> (or template "")
                   (str/replace "{{project-dir}}" (or project-dir ""))
                   (str/replace "{{context}}" (str "Git History Analysis:\n\n"
                                                   (or git-data "")
                                                   "\n\nTimeline:\n"
                                                   (or timeline ""))))]
    {:text prompt
     :status :ok}))

(defn run-deep-git-analysis
  "Run comprehensive deep git analysis"
  [{:keys [ctx project-dir limit]
    :or {limit 500}}]
  (let [dir (or project-dir (:project-dir ctx) ".")]
    (deep-git-analysis {:dir dir :limit limit})))

(defn run-pr-analysis
  "Run comprehensive PR review analysis between two branches"
  [{:keys [ctx project-dir base-branch head-branch]
    :or {base-branch "main"}}]
  (let [dir (or project-dir (:project-dir ctx) ".")
        base (or base-branch (System/getenv "PR_BASE_BRANCH") "main")
        head (or head-branch (System/getenv "PR_HEAD_BRANCH") "HEAD")]
    (analyze-pr {:dir dir :base-branch base :head-branch head})))

(defn ensure-git-branches
  "Fetch from origin and ensure branches are synced with remote"
  [{:keys [ctx project-dir base-branch head-branch]}]
  (let [dir (or project-dir (:project-dir ctx) ".")
        base (or base-branch (System/getenv "PR_BASE_BRANCH") "main")
        head (or head-branch (System/getenv "PR_HEAD_BRANCH") "HEAD")]
    (try
      ;; Fetch all from origin
      (println "📥 Fetching from origin...")
      (let [fetch-result (shell/sh "git" "fetch" "origin" "--prune" :dir dir)]
        (when-not (zero? (:exit fetch-result))
          (println "⚠️  Fetch warning:" (:err fetch-result))))

      ;; For each branch, ensure it's synced with remote
      (letfn [(sync-branch [branch]
                (when-not (or (str/starts-with? branch "HEAD")
                              (re-matches #"^[0-9a-f]{7,40}$" branch))
                  (let [remote-branch (str "origin/" branch)
                        check-remote (shell/sh "git" "show-ref" "--verify" "--quiet"
                                               (str "refs/remotes/" remote-branch) :dir dir)]
                    (when (zero? (:exit check-remote))
                      (let [check-local (shell/sh "git" "show-ref" "--verify" "--quiet"
                                                  (str "refs/heads/" branch) :dir dir)]
                        (if (zero? (:exit check-local))
                          ;; Local exists - reset to remote
                          (do
                            (println (str "🔄 Syncing " branch " with " remote-branch))
                            (shell/sh "git" "checkout" branch :dir dir)
                            (shell/sh "git" "reset" "--hard" remote-branch :dir dir))
                          ;; Local doesn't exist - create from remote
                          (do
                            (println (str "📥 Creating " branch " from " remote-branch))
                            (shell/sh "git" "checkout" "-b" branch remote-branch :dir dir))))))))]

        (sync-branch base)
        (sync-branch head))

      ;; Get commit info for both branches
      (let [base-commit (str/trim (:out (shell/sh "git" "rev-parse" "--short" base :dir dir)))
            head-commit (str/trim (:out (shell/sh "git" "rev-parse" "--short" head :dir dir)))
            commit-count (:out (shell/sh "git" "rev-list" "--count"
                                         (str base ".." head) :dir dir))]

        {:status :ok
         :text (str "✓ Git branches synced\n"
                    "  Base: " base " (" base-commit ")\n"
                    "  Head: " head " (" head-commit ")\n"
                    "  Commits to review: " (str/trim commit-count) "\n")
         :base-branch base
         :head-branch head})

      (catch Exception e
        {:status :error
         :text (str "Failed to prepare branches: " (.getMessage e))}))))


(defn format-pr-summary
  "Format PR analysis into a lightweight summary without bulky diffs.
   Includes file lists, statistics, and metadata but excludes diff-context."
  [{:keys [analysis]}]
  (let [data (if (map? analysis) analysis {})
        {:keys [summary risk sensitive-files behavior-changes api-changes test-gap
                all-files files-by-type suggested-reviewers]} data]
    
    {:status :ok
     :text
     (str "# PR Analysis: " (:title summary "Unknown") "
"
          "**Author:** " (:author summary "Unknown") "
"
          "**Base:** " (:base-branch summary) " | **Head:** " (:head-branch summary) "
"
          "**Stats:** " (:commits summary 0) " commits, " 
          (:files summary 0) " files changed, "        
          (:total-lines summary 0) " lines affected.

"

          "## Risk Assessment: " (str/upper-case (name (or (:level risk) :low))) 
          " (Score: " (or (:score risk) 0) ")
"
          (str/join "
" (map #(str "- " %) (:reasons risk [])))
          "

"

          "## Files Changed by Type

"
          (when all-files
            (let [by-type (group-by :type all-files)]
              (str/join "

"
                        (for [[file-type files] (sort-by key by-type)]
                          (str "### " (str/capitalize (name file-type)) 
                               " Files (" (count files) ")
"
                               (str/join "
"
                                         (for [f files]
                                           (str "- `" (:file f) "` "
                                                "(+" (:added f 0) " / -" (:deleted f 0) ") "
                                                "- " (name (:change-type f :unknown))))))))))
          "

"

          (when (seq sensitive-files)
            (str "### ⚠️ Sensitive Files Touched
"
                 (str/join "
" (for [f sensitive-files]
                                  (str "- `" (:file f) "` (detected: " 
                                       (str/join ", " (map name (keys (:sensitive f)))) ")")))
                 "

"))

          (when (seq behavior-changes)
            (str "### ⚙️ Behavior Changes
"
                 (str/join "
" (for [f behavior-changes] 
                                  (str "- `" (:file f) "`")))
                 "

"))

          (when (seq api-changes)
            (str "### 🔌 API Changes
"
                 (str/join "
" (for [f api-changes] 
                                  (str "- `" (:file f) "`")))
                 "

"))

          (when (:has-gap test-gap)
            (str "### 🧪 Test Gap Detected
"
                 "Source files were changed but no corresponding test changes were found.
"
                 "- Source files changed: " (:source-files test-gap 0) "
"
                 "- Test files changed: " (:test-files test-gap 0) "

"))

          (when (seq suggested-reviewers)
            (str "## Suggested Reviewers
"
                 (str/join "
" (for [r (take 5 suggested-reviewers)]
                                  (str "- " (:name r) " (" (:email r) ") - " 
                                       (:total-commits r) " commits in changed files")))
                 "

")))}))
