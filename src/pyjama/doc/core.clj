(ns pyjama.doc.core
  "Orchestrates LLM-assisted documentation generation and provides the CLI entry point.

   - Aggregates content from files matched by patterns into Markdown (via pyjama.doc.utils).
   - Calls an LLM (pyjama.core/agent) to produce the main review and writes a .md file.
     If :out-file is a directory or lacks an extension, a timestamped <yyyy-MM-dd_HH-mm-ss>.md is created.
   - Optionally renders a PDF (pyjama.tools.pandoc) and runs a second summarization pass,
     writing <out>_summary.md when :summary is truthy.
   - Accepts EDN config paths or path/glob arguments on the CLI; see process-review and -main."
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [pyjama.core :as agent]
            [pyjama.doc.utils :as u]
            [pyjama.helpers.config :as hc]
            [pyjama.helpers.file :as hf]
            [pyjama.doc.spinner :as spinner]
            [pyjama.tools.pandoc])
  (:import (java.io File)
           (java.time ZoneId ZonedDateTime)
           (java.time.format DateTimeFormatter))
  (:gen-class))


;; ---------- helpers ----------

(defn ^:private timestamp-utc []
  (.format (ZonedDateTime/now (ZoneId/of "UTC"))
           (DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss'Z'")))

(defn ^:private ensure-str [x]
  (cond
    (string? x) x
    (nil? x) ""
    :else (str x)))

(defn ^:private has-extension? [^File f]
  (some? (hf/file-ext f)))


(defn ^String expand-env
  "Expand path vars in a string.
   - Leading ~ -> user.home
   - ${key} -> system property first, else env
   - %VAR%  -> env (or system property)
   - $VAR   -> env (or system property). Won't fire for ${...}.
   Custom tokens:
   - ${cwd.name}         -> current directory name (basename of user.dir)
   - ${cwd.parent.name}  -> parent directory name
   Unknown vars are left as-is."
  [^String s]
  (let [env (System/getenv)
        props (System/getProperties)
        home (System/getProperty "user.home")
        cwd (System/getProperty "user.dir")
        cwd-file (io/file cwd)
        cwd-name (.getName cwd-file)
        parent (.getParentFile cwd-file)
        parent-name (when parent (.getName parent))
        lookup (fn [k]
                 (case k
                   "cwd.name" cwd-name
                   "cwd.parent.name" (or parent-name "")
                   "cwd" cwd
                   (or (.get props k) (get env k))))]
    (-> s
        ;; ~ at start
        (str/replace #"^~(?=$|[/\\])" home)
        ;; ${key} first (system props -> env -> custom tokens)
        (str/replace #"\$\{([^}]+)\}"
                     (fn [[_ k]] (or (lookup k) (str "${" k "}"))))
        ;; %VAR% (Windows style)
        (str/replace #"%([A-Za-z_][A-Za-z0-9_]*)%"
                     (fn [[_ k]] (or (lookup k) (str "%" k "%"))))
        ;; $VAR BUT NOT ${...}
        (str/replace #"\$(?!\{)([A-Za-z_][A-Za-z0-9_]*)"
                     (fn [[_ k]] (or (lookup k) (str "$" k)))))))

(defn resolve-output-file
  "Return the actual File to write. If out-file is:
   - nil: write to ./pyjama-doc/<timestamp>.md
   - a directory: <dir>/<timestamp>.md
   - a file with no extension: treat as dir, write <path>/<timestamp>.md
   - a file with extension: use as-is
   Supports env/sys-prop expansion in string paths."
  [out-file]
  (let [default-dir (io/file "pyjama-doc")
        f (cond
            (instance? File out-file) out-file
            (string? out-file) (io/file (expand-env out-file))
            (nil? out-file) default-dir
            :else (io/file (expand-env (str out-file))))]
    (cond
      (.isDirectory ^File f) (io/file f (str (timestamp-utc) ".md"))
      (not (has-extension? ^File f)) (io/file f (str (timestamp-utc) ".md"))
      :else f)))


(defn ^:private summary-file
  "Given the primary output file (File or String), return the summary file:
   <same path> with `_summary.md`."
  ^File [final-file]
  (let [final-file (io/as-file final-file)
        parent (.getParentFile ^File final-file)
        name (.getName ^File final-file)
        ext (some-> name io/as-file hf/file-ext)
        base (if ext
               (subs name 0 (- (count name) (inc (count ext))))
               name)]
    (io/file parent (str base "_summary.md"))))

(defn ^:private deep-merge
  "Deep merge maps. For vectors or seqs, concatenate. Rightmost wins for scalars.
   Use for combining multiple config sources."
  [& ms]
  (letfn [(m* [a b]
            (cond
              (and (map? a) (map? b)) (merge-with m* a b)
              (and (sequential? a) (sequential? b)) (into (empty a) (concat a b))
              (and (nil? a) (some? b)) b
              :else b))]
    (reduce m* {} ms)))

(defn ^:private normalize-config [cfg]
  (let [patterns (vec (:patterns cfg))
        model (or (:model cfg) {})
        out-file (:out-file cfg)
        spinner? (if (contains? cfg :spinner?)
                   (boolean (:spinner? cfg))
                   (spinner/tty?))]                         ;; default: show spinner in interactive TTYs
    (merge {:patterns patterns
            :model    model
            :out-file out-file
            :system   (:system cfg)
            :pre      (:pre cfg)
            :pdf      (boolean (:pdf cfg))
            :summary  (:summary cfg)
            :spinner? spinner?}
           (select-keys cfg [:some-future-keys]))))

(defn ^:private call-agent->string
  "Runs agent/call and ensures string; label is used by the spinner."
  [spinner? label agent-input]
  (let [res (spinner/with-spinner spinner? label #(agent/call agent-input))]
    (ensure-str res)))
;; ---------- main ----------

(def default-summary-pre
  "Generate a short summary, in two sections based on the text that follows:
- One section with a slide-like title and bullet points.
- One section with a simple 2-column table capturing key ideas and notes.
Keep it concise and factual.\n"

  "%s")

(defn process-review
  "Runs the main LLM call on aggregated Markdown from :patterns.
   If :summary is truthy, runs a second pass on the produced text.

   Config keys:
   - :patterns (vector of string or {:pattern :metadata})
   - :model    (map) passed into agent/call
   - :out-file (string|File|nil) target path or directory; defaults to ./pyjama-doc/<ts>.md
   - :system   (string|nil)
   - :pre      (string|nil) preamble for the first pass
   - :pdf      (boolean) render a PDF next to the main output
   - :summary  (boolean|string) if string, used as summary preamble"
  [{:keys [patterns model out-file system pre pdf summary] :as cfg}]
  (let [cfg* (normalize-config cfg)
        combined-md (u/aggregate-md-from-patterns (:patterns cfg*))
        agent-input-1 (merge (:model cfg*)
                             {:system system
                              :pre    pre
                              :prompt [combined-md]})
        result-1 (call-agent->string (:spinner? cfg*) "Generating review" agent-input-1)
        final-file (resolve-output-file out-file)]
    (io/make-parents final-file)
    (spit final-file result-1 :encoding "UTF-8")
    (when pdf
      (try
        (pyjama.tools.pandoc/md->pdf {:input  final-file
                                      :output (str final-file ".pdf")})
        (catch Throwable t
          (binding [*out* *err*]
            (println "WARN: PDF generation failed:" (.getMessage t))))))
    (when summary
      (let [sum-pre (cond
                      (string? summary) summary
                      :else default-summary-pre)
            ;; Either format sum-pre to include the text or pass the text via :prompt.
            ;; Here we keep the text in :prompt to avoid giant preambles.
            agent-input-2 (merge (:model cfg*)
                                 {:system system
                                  :pre    sum-pre
                                  :prompt [result-1]})
            result-2 (call-agent->string (:spinner? cfg*) "Summarizing" agent-input-2)
            sum-file (summary-file final-file)]
        (io/make-parents sum-file)
        (spit sum-file result-2 :encoding "UTF-8")))
    {:out     (.getPath ^File final-file)
     :summary (when summary (.getPath (summary-file final-file)))
     :pdf     (when pdf (str final-file ".pdf"))}))

(defn edn-file? [s]
  (and (string? s)
       (str/ends-with? (str/lower-case (str/trim s)) ".edn")))

(defn -main [& args]
  (if (empty? args)
    (do
      (println "Usage:")
      (println "  pyjama.doc.core <conf1.edn[,conf2.edn,...]> [pattern ...]")
      (println "Examples:")
      (println "  pyjama.doc.core ./doc.conf.edn src/**/*.clj README.md")
      (println "  pyjama.doc.core conf.a.edn,conf.b.edn src/**/*.clj")
      (println "  pyjama.doc.core src/**/*.clj README.md   ; no config, all patterns"))
    (try
      (let [first-arg (first args)
            rest-args (rest args)
            ;; Split the first arg on commas and keep only .edn files
            edn-files (->> (str/split (or first-arg "") #",")
                           (map str/trim)
                           (remove str/blank?)
                           (filter edn-file?))
            using-config? (seq edn-files)

            ;; Load config only if we actually found any .edn files
            base-cfg (if using-config?
                       (hc/load-config edn-files)
                       {})

            ;; Patterns: if we had configs, they start from rest-args; otherwise all args are patterns
            patterns (if using-config? rest-args args)
            merged-cfg (deep-merge
                         base-cfg
                         {:patterns (->> patterns (map (fn [p] {:pattern p})) vec)})
            final-cfg (normalize-config merged-cfg)]
        (println "Effective config:")
        (pprint final-cfg)
        (let [res (process-review final-cfg)]
          (println "Wrote:" (:out res))
          (when-let [s (:summary res)] (println "Wrote summary:" s))
          (when-let [p (:pdf res)] (println "Wrote PDF:" p))))
      (catch Throwable t
        (binding [*out* *err*]
          (println "ERROR:" (.getMessage t)))
        (System/exit 1)))))
