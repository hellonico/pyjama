(ns pyjama.doc.core
  (:require [pyjama.core :as agent]
            [pyjama.helpers.config :as hc]
            [pyjama.doc.utils :as u]
            [pyjama.tools.pandoc]
            [clojure.java.io :as io])
  (:import (java.io File)
           (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

;; ---------- helpers ----------

(defn ^:private timestamp []
  (.format (LocalDateTime/now)
           (DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")))

(defn resolve-output-file
  "Return the actual File to write to.
   If out-file is a directory or has no extension, use <dir>/<yyyy-MM-dd_HH-mm-ss>.md."
  [out-file]
  (let [f (io/file out-file)
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
  "If :summary true, performs a second LLM call over the first call's output and writes
   `<previous out-file>_summary.md`."
  [{:keys [patterns model out-file system pre pdf summary]}]
  (let [combined-md (u/aggregate-md-from-patterns patterns)
        result-1 (agent/call
                   (merge model
                          {:system system
                           :pre    pre
                           :prompt [combined-md]}))
        final-file (resolve-output-file out-file)
        out-1-str (with-out-str (println result-1))]
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
      (let [sum-pre "Generated a short summary, (with title and points just like a ppt slide)  of %s"
            result-2 (agent/call
                       (merge model
                              {:system system
                               :pre    sum-pre
                               :prompt [out-1-str]}))
            sum-file (summary-file final-file)]
        (io/make-parents sum-file)
        (spit sum-file (with-out-str (println result-2)))))

    ;; return the path(s) for convenience
    {:out     (.getPath final-file)
     :summary (when summary (.getPath (summary-file final-file)))
     :pdf     (when pdf (str final-file ".pdf"))}))

(defn -main [& args]
  (process-review (hc/load-config args))
  ;(process-review "resources/reporting/edn_config.edn")
  ;(process-review "resources/reporting/bad_review.edn")
  ;(process-review (hf/load-config "resources/reporting/yourown.edn"))
  )
