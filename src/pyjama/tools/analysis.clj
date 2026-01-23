(ns pyjama.tools.analysis
  "Advanced codebase analysis tools for discovering patterns, dependencies, and insights"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.edn :as edn])
  (:import (java.io File)))

;; =============================================================================
;; File Discovery and Categorization
;; =============================================================================

(defn discover-codebase
  "Recursively discover all code files in a directory with metadata"
  [{:keys [dir extensions exclude-patterns]
    :or {extensions ["clj" "cljc" "cljs" "edn" "md" "java" "js" "ts" "py" "rb"]
         exclude-patterns ["target" "node_modules" ".git" "out" "classes"]}}]
  (let [root (io/file (or dir "."))
        exclude-re (re-pattern (str "(" (str/join "|" exclude-patterns) ")"))]
    (when-not (.exists root)
      (throw (ex-info "Directory not found" {:dir dir})))
    (let [files (->> (file-seq root)
                     (filter #(.isFile ^File %))
                     (remove #(re-find exclude-re (.getPath ^File %)))
                     (filter #(some (fn [ext] (str/ends-with? (.getName ^File %) (str "." ext)))
                                    extensions))
                     (map (fn [^File f]
                            (let [path (.getAbsolutePath f)
                                  rel-path (str/replace path (str (.getAbsolutePath root) "/") "")
                                  ext (last (str/split (.getName f) #"\."))]
                              {:file path
                               :relative-path rel-path
                               :name (.getName f)
                               :extension ext
                               :size (.length f)
                               :lines (with-open [rdr (io/reader f)]
                                        (count (line-seq rdr)))
                               :content (slurp f)})))
                     vec)]
      {:status :ok
       :total-files (count files)
       :total-lines (reduce + (map :lines files))
       :total-size (reduce + (map :size files))
       :files files
       :text (format "Discovered %d files (%d lines, %d bytes)"
                     (count files)
                     (reduce + (map :lines files))
                     (reduce + (map :size files)))})))

(def discover-codebase-with-gitignore discover-codebase)

;; =============================================================================
;; Dependency Analysis
;; =============================================================================

(defn extract-dependencies
  "Extract dependencies from Clojure files (ns requires, imports)"
  [{:keys [files ctx]}]
  (let [files (or files (:project-files ctx) [])
        clj-files (filter #(#{"clj" "cljc" "cljs"} (:extension %)) files)
        deps (for [f clj-files]
               (let [content (:content f)
                     ns-form (try
                               (read-string content)
                               (catch Exception _ nil))
                     requires (when (and (seq? ns-form) (= 'ns (first ns-form)))
                                (let [ns-name (second ns-form)
                                      clauses (drop 2 ns-form)]
                                  {:namespace ns-name
                                   :file (:relative-path f)
                                   :requires (->> clauses
                                                  (filter #(and (seq? %) (= :require (first %))))
                                                  (mapcat rest)
                                                  (map (fn [r]
                                                         (cond
                                                           (vector? r) (first r)
                                                           (symbol? r) r
                                                           :else nil)))
                                                  (remove nil?)
                                                  vec)
                                   :imports (->> clauses
                                                 (filter #(and (seq? %) (= :import (first %))))
                                                 (mapcat rest)
                                                 vec)}))]
                 requires))
        valid-deps (remove nil? deps)
        all-namespaces (set (map :namespace valid-deps))
        external-deps (set (mapcat :requires valid-deps))
        internal-deps (set/intersection all-namespaces external-deps)
        external-only (set/difference external-deps all-namespaces)]
    {:status :ok
     :dependencies valid-deps
     :total-namespaces (count all-namespaces)
     :internal-dependencies (count internal-deps)
     :external-dependencies (count external-only)
     :text (format "Found %d namespaces with %d internal and %d external dependencies"
                   (count all-namespaces)
                   (count internal-deps)
                   (count external-only))}))

;; =============================================================================
;; Complexity Analysis
;; =============================================================================

(defn analyze-complexity
  "Analyze code complexity metrics"
  [{:keys [files ctx]}]
  (let [files (or files (:project-files ctx) [])
        clj-files (filter #(#{"clj" "cljc" "cljs"} (:extension %)) files)
        metrics (for [f clj-files]
                  (let [content (:content f)
                        lines (:lines f)
                        ;; Simple complexity heuristics
                        defn-count (count (re-seq #"\(defn\s" content))
                        defn-count-private (count (re-seq #"\(defn-\s" content))
                        def-count (count (re-seq #"\(def\s" content))
                        let-count (count (re-seq #"\(let\s" content))
                        if-count (count (re-seq #"\(if\s" content))
                        cond-count (count (re-seq #"\(cond\s" content))
                        loop-count (count (re-seq #"\(loop\s" content))
                        recur-count (count (re-seq #"recur" content))
                        comment-lines (count (re-seq #"^\s*;" content))
                        avg-line-length (if (pos? lines)
                                          (/ (count content) lines)
                                          0)
                        complexity-score (+ (* defn-count 2)
                                            (* if-count 1)
                                            (* cond-count 2)
                                            (* loop-count 3)
                                            (* recur-count 1))]
                    {:file (:relative-path f)
                     :lines lines
                     :functions (+ defn-count defn-count-private)
                     :public-functions defn-count
                     :private-functions defn-count-private
                     :definitions def-count
                     :let-bindings let-count
                     :conditionals (+ if-count cond-count)
                     :loops loop-count
                     :recursions recur-count
                     :comment-lines comment-lines
                     :avg-line-length (int avg-line-length)
                     :complexity-score complexity-score}))
        sorted-by-complexity (reverse (sort-by :complexity-score metrics))
        top-complex (take 10 sorted-by-complexity)]
    {:status :ok
     :metrics metrics
     :top-complex top-complex
     :total-functions (reduce + (map :functions metrics))
     :total-complexity (reduce + (map :complexity-score metrics))
     :text (str "Complexity Analysis:\n"
                "Total functions: " (reduce + (map :functions metrics)) "\n"
                "Total complexity score: " (reduce + (map :complexity-score metrics)) "\n\n"
                "Top 10 most complex files:\n"
                (str/join "\n" (map #(format "  %s (score: %d, %d functions, %d lines)"
                                             (:file %)
                                             (:complexity-score %)
                                             (:functions %)
                                             (:lines %))
                                    top-complex)))}))

;; =============================================================================
;; Pattern Detection
;; =============================================================================

(defn detect-patterns
  "Detect common patterns and anti-patterns in the codebase"
  [{:keys [files ctx]}]
  (let [files (or files (:project-files ctx) [])
        clj-files (filter #(#{"clj" "cljc" "cljs"} (:extension %)) files)
        patterns (for [f clj-files]
                   (let [content (:content f)]
                     {:file (:relative-path f)
                      :patterns
                      {:threading-macros (+ (count (re-seq #"->" content))
                                            (count (re-seq #"->>" content)))
                       :destructuring (count (re-seq #"\[.*:as\s" content))
                       :protocols (count (re-seq #"\(defprotocol\s" content))
                       :multimethods (count (re-seq #"\(defmulti\s" content))
                       :records (count (re-seq #"\(defrecord\s" content))
                       :atoms (count (re-seq #"\(atom\s" content))
                       :refs (count (re-seq #"\(ref\s" content))
                       :agents (count (re-seq #"\(agent\s" content))
                       :async-channels (count (re-seq #"async/chan" content))
                       :core-async (count (re-seq #"async/" content))
                       :spec-usage (count (re-seq #"s/def\s" content))
                       :test-cases (count (re-seq #"\(deftest\s" content))
                       :docstrings (count (re-seq #"\"[^\"]+\"\s*\[" content))}}))
        total-patterns (reduce (fn [acc {:keys [patterns]}]
                                 (merge-with + acc patterns))
                               {}
                               patterns)]
    {:status :ok
     :file-patterns patterns
     :total-patterns total-patterns
     :text (str "Pattern Detection:\n"
                "Threading macros: " (:threading-macros total-patterns 0) "\n"
                "Destructuring: " (:destructuring total-patterns 0) "\n"
                "Protocols: " (:protocols total-patterns 0) "\n"
                "Multimethods: " (:multimethods total-patterns 0) "\n"
                "Records: " (:records total-patterns 0) "\n"
                "State management (atoms/refs/agents): "
                (+ (:atoms total-patterns 0)
                   (:refs total-patterns 0)
                   (:agents total-patterns 0)) "\n"
                "Core.async usage: " (:core-async total-patterns 0) "\n"
                "Test cases: " (:test-cases total-patterns 0))}))

;; =============================================================================
;; Architecture Analysis
;; =============================================================================

(defn analyze-architecture
  "Analyze high-level architecture and module organization"
  [{:keys [files dependencies ctx]}]
  (let [files (or files (:project-files ctx) [])
        clj-files (filter #(#{"clj" "cljc" "cljs"} (:extension %)) files)
        ;; Group by top-level namespace
        by-namespace (group-by (fn [f]
                                 (let [path (:relative-path f)
                                       parts (str/split path #"/")]
                                   (if (> (count parts) 1)
                                     (second parts)
                                     "root")))
                               clj-files)
        modules (for [[ns-name files] by-namespace]
                  {:module ns-name
                   :file-count (count files)
                   :total-lines (reduce + (map :lines files))
                   :total-size (reduce + (map :size files))})
        sorted-modules (reverse (sort-by :total-lines modules))]
    {:status :ok
     :modules sorted-modules
     :total-modules (count modules)
     :text (str "Architecture Analysis:\n"
                "Total modules: " (count modules) "\n\n"
                "Modules by size:\n"
                (str/join "\n" (map #(format "  %s: %d files, %d lines"
                                             (:module %)
                                             (:file-count %)
                                             (:total-lines %))
                                    sorted-modules)))}))

;; =============================================================================
;; Code Quality Metrics
;; =============================================================================

(defn analyze-quality
  "Analyze code quality indicators"
  [{:keys [files ctx]}]
  (let [files (or files (:project-files ctx) [])
        clj-files (filter #(#{"clj" "cljc" "cljs"} (:extension %)) files)
        quality-metrics (for [f clj-files]
                          (let [content (:content f)
                                lines (:lines f)
                                code-lines (count (remove str/blank? (str/split-lines content)))
                                comment-lines (count (re-seq #"^\s*;" content))
                                docstring-count (count (re-seq #"\"[^\"]+\"\s*\[" content))
                                function-count (count (re-seq #"\(defn" content))
                                comment-ratio (if (pos? code-lines)
                                                (/ comment-lines code-lines)
                                                0)
                                doc-ratio (if (pos? function-count)
                                            (/ docstring-count function-count)
                                            0)]
                            {:file (:relative-path f)
                             :lines lines
                             :code-lines code-lines
                             :comment-lines comment-lines
                             :comment-ratio (float comment-ratio)
                             :docstring-count docstring-count
                             :function-count function-count
                             :documentation-ratio (float doc-ratio)
                             :quality-score (+ (* comment-ratio 30)
                                               (* doc-ratio 70))}))
        avg-quality (if (seq quality-metrics)
                      (/ (reduce + (map :quality-score quality-metrics))
                         (count quality-metrics))
                      0)
        well-documented (filter #(> (:quality-score %) 50) quality-metrics)
        needs-docs (filter #(< (:quality-score %) 20) quality-metrics)]
    {:status :ok
     :metrics quality-metrics
     :average-quality-score (float avg-quality)
     :well-documented-count (count well-documented)
     :needs-documentation-count (count needs-docs)
     :text (str "Code Quality Analysis:\n"
                "Average quality score: " (format "%.1f" (double avg-quality)) "/100\n"
                "Well documented files: " (count well-documented) "\n"
                "Files needing documentation: " (count needs-docs) "\n\n"
                (when (seq needs-docs)
                  (str "Files needing better documentation:\n"
                       (str/join "\n" (map #(format "  %s (score: %.1f)"
                                                    (:file %)
                                                    (double (:quality-score %)))
                                           (take 10 needs-docs))))))}))

;; =============================================================================
;; Generate Summary Report
;; =============================================================================

(defn generate-summary
  "Generate a comprehensive summary of all analyses"
  [{:keys [discovery dependencies complexity patterns architecture quality]}]
  {:status :ok
   :text (str "# Codebase Analysis Summary\n\n"
              "## Overview\n"
              (:text discovery) "\n\n"
              "## Dependencies\n"
              (:text dependencies) "\n\n"
              "## Complexity\n"
              (:text complexity) "\n\n"
              "## Patterns\n"
              (:text patterns) "\n\n"
              "## Architecture\n"
              (:text architecture) "\n\n"
              "## Quality\n"
              (:text quality) "\n")})

(defn format-discovered-files
  "Format discovered files from discover-codebase-with-gitignore into a readable list.
   Args: {:keys [ctx]}"
  [{:keys [ctx]}]
  (let [last-obs (:last-obs ctx)
        files (or (:project-files last-obs)
                  (:files last-obs)
                  [])
        ;; Group files by directory for better readability
        by-dir (group-by #(or (when-let [rp (:relative-path %)]
                                (let [parts (str/split rp #"/")]
                                  (if (> (count parts) 1)
                                    (str/join "/" (butlast parts))
                                    ".")))
                              ".")
                         files)
        formatted (str "## Available Files (respecting .gitignore)\n\n"
                       "Total: " (count files) " files\n\n"
                       (str/join "\n\n"
                                 (for [[dir file-list] (sort-by key by-dir)]
                                   (str "### " dir "/\n"
                                        (str/join "\n"
                                                  (map #(str "- " (:relative-path %))
                                                       (sort-by :relative-path file-list)))))))]
    {:status :ok
     :files files
     :project-files files
     :text formatted}))

(defn format-analysis-results
  "Format parallel analysis results into a single text block for synthesis"
  [{:keys [ctx]}]
  (let [trace (:trace ctx)
        ;; Find the parallel-analysis step in the trace
        parallel-step (first (filter #(= :parallel-analysis (:step %)) trace))
        merged-results (get-in parallel-step [:obs :merged])
        ;; Extract the text from each analysis - they're nested under :obs :text
        complexity-text (or (get-in merged-results [:analyze-complexity :obs :text])
                            "No complexity analysis available")
        patterns-text (or (get-in merged-results [:analyze-patterns :obs :text])
                          "No pattern analysis available")
        architecture-text (or (get-in merged-results [:analyze-architecture :obs :text])
                              "No architecture analysis available")
        quality-text (or (get-in merged-results [:analyze-quality :obs :text])
                         "No quality analysis available")
        formatted (str "## Complexity Analysis\n\n" complexity-text "\n\n"
                       "## Pattern Analysis\n\n" patterns-text "\n\n"
                       "## Architecture Analysis\n\n" architecture-text "\n\n"
                       "## Quality Analysis\n\n" quality-text)]
    {:text formatted
     :status :ok}))

(defn calculate-health-score
  "Aggegate metrics from various analyses into a unified health scorecard.
   Expected inputs in ctx (from parallel analysis):
   - complexity: {:total-complexity, :total-functions, ...}
   - quality: {:metrics [{:quality-score ...}], :average-quality-score ...}
   - patterns: {:total-patterns ...}"
  [{:keys [ctx]}]
  (let [trace (:trace ctx)
        ;; Find data in trace (handling potentially different agent structures)
        ;; Default expectation: :parallel-analysis step has merged results
        parallel-step (first (filter #(= :parallel-analysis (:step %)) trace))
        merged (get-in parallel-step [:obs :merged])

        ;; Extract raw data
        comp-data (get-in merged [:analyze-complexity :obs])
        qual-data (get-in merged [:analyze-quality :obs])
        arch-data (get-in merged [:analyze-architecture :obs])

        ;; 1. Complexity Score (Lower is better, inverted to 0-100)
        ;; Heuristic: Avg complexity per function > 10 is bad. > 5 is warning.
        total-c (or (:total-complexity comp-data) 0)
        total-f (or (:total-functions comp-data) 1)
        avg-c (if (pos? total-f) (double (/ total-c total-f)) 0.0)
        ;; Score: 100 - (avg-c * 10). If avg is 1, score 90. If avg 10, score 0.
        score-c (max 0 (min 100 (- 100 (* avg-c 8.0))))

        ;; 2. Quality/Docs Score (Already 0-100 from tool)
        score-q (or (:average-quality-score qual-data) 0)

        ;; 3. Architecture/Modularity Score
        ;; Heuristic: Big files are bad.
        modules (or (:modules arch-data) [])
        bloated-modules (count (filter #(> (:total-lines %) 1000) modules))
        score-a (max 0 (- 100 (* bloated-modules 15)))

        ;; Weighted Total
        final-score (int (/ (+ (* score-c 0.4) (* score-q 0.4) (* score-a 0.2)) 1.0))

        grade (cond
                (>= final-score 90) "A"
                (>= final-score 80) "B"
                (>= final-score 70) "C"
                (>= final-score 50) "D"
                :else "F")

        status (if (>= final-score 70) "PASS" "NEEDS IMPROVEMENT")]

    {:status :ok
     :scores {:complexity {:score (int score-c) :avg-complexity (float avg-c)}
              :quality {:score (int score-q)}
              :architecture {:score (int score-a) :bloated-modules bloated-modules}
              :overall final-score
              :grade grade
              :status status}
     :text (str "# System Health Dashboard\n\n"
                "## Overall Score: " final-score " (" grade ") - " status "\n\n"
                "### Component Scores\n"
                "- **Complexity:** " (int score-c) "/100 (Avg cyclomatic: " (format "%.1f" avg-c) ")\n"
                "- **Code Quality:** " (int score-q) "/100 (Documentation & Comments)\n"
                "- **Architecture:** " (int score-a) "/100 (" bloated-modules " bloated modules)\n")}))
