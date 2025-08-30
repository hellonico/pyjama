(ns pyjama.doc.core
  "Orchestrates LLM-assisted documentation generation and provides the CLI entry point.

   - Aggregates content from files matched by patterns into Markdown (via pyjama.doc.utils).
   - Calls an LLM (pyjama.core/agent) to produce the main review and writes a .md file.
     If :out-file is a directory or lacks an extension, a timestamped <yyyy-MM-dd_HH-mm-ss>.md is created.
   - Optionally renders a PDF (pyjama.tools.pandoc) and runs a second summarization pass,
     writing <out>_summary.md when :summary is truthy.
   - Accepts EDN config paths or path/glob arguments on the CLI; see process-review and -main."
  (:require [clojure.string :as str]
            [pyjama.core :as agent]
            [pyjama.helpers.config :as hc]
            [pyjama.doc.utils :as u]
            [clojure.pprint :refer [pprint]]
            [pyjama.tools.pandoc]
            [clojure.java.io :as io])
  (:import (java.io File)
           (java.time LocalDateTime)
           (java.time.format DateTimeFormatter))
  (:gen-class))

;; ---------- helpers ----------

(defn ^:private timestamp []
  (.format (LocalDateTime/now)
           (DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")))

(defn resolve-output-file
  "Return the actual File to write to.
   If out-file is a directory or has no extension, use <dir>/<yyyy-MM-dd_HH-mm-ss>.md."
  [out-file]
  (let [f (io/file (apply str out-file))
        as-dir? (or (.isDirectory f)
                    (not (re-find #"\.[^/\\]+$" (.getName f))))]
    (if as-dir?
      (io/file f (str (timestamp) ".md"))
      f)))

(defn ^:private summary-file
  "Given the primary output file, return the summary file: <same path> with `_summary.md`."
  ^File [^File final-file]
  (let [parent (.getParentFile final-file)
        name (.getName final-file)
        base (if (re-find #"\.md$" name)
               (subs name 0 (- (count name) 3))
               name)]
    (io/file parent (str base "_summary.md"))))

;; ---------- main ----------

(defn process-review
  "If :summary true or a string, performs a second LLM call over the first call's output.
   When true, uses the default summary preamble. When a string, uses it as the preamble.
   Writes `<previous out-file>_summary.md`."
  [{:keys [patterns model out-file system pre pdf summary]}]
  (let [combined-md (u/aggregate-md-from-patterns patterns)
        result-1    (agent/call
                      (merge model
                             {:system system
                              :pre    pre
                              :prompt [combined-md]}))
        final-file  (resolve-output-file out-file)
        out-1-str   (with-out-str (println result-1))]
    ;; write main result
    (io/make-parents final-file)
    (spit final-file out-1-str)

    ;; optional PDF for main result
    (when pdf
      (pyjama.tools.pandoc/md->pdf
        {:input  final-file
         :output (str final-file ".pdf")}))

    ;; optional summary step
    (when summary
      (let [default-pre "This is a text: %s\nGenerate a short summary, in two sections:
      - One with title and points just like a ppt slide.
      - One with a simple table for an ideas overview of the text.\n"
            sum-pre (if (string? summary) summary default-pre)
            result-2 (agent/call
                       (merge model
                              {:system system
                               :pre    sum-pre
                               :prompt [out-1-str]}))
            sum-file (summary-file final-file)]
        (io/make-parents sum-file)
        (spit sum-file (with-out-str (println result-2)))))

    ;; return the path(s) for convenience
    {:out     (.getPath ^File final-file)
     :summary (when summary (.getPath (summary-file final-file)))
     :pdf     (when pdf (str final-file ".pdf"))}))

(defn arg->config [arg]
  (if (str/ends-with? arg ".edn")
    (hc/load-config [arg])
    {:patterns [{:pattern arg}]}))

(defn -main [& args]
  (if (empty? args)
    (println "No arguments provided\n")
    (let [configs (map arg->config args)
          merged  (apply merge-with concat configs)]
      (pprint merged)
      (process-review merged))))