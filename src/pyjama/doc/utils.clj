(ns pyjama.doc.utils
  (:require [clojure.string :as str]
            [clojure.tools.reader :as tr]
            [clojure.tools.reader.reader-types :as rrt]
            [pyjama.helpers.file :as hf])                       ;; <-- ensure this is required
  (:import (java.io File)) )  ;; <-- new imports


(def text-exts #{"md" "txt"})
(def code-exts #{"clj" "cljc" "cljs"})

(defn- extract-ns-doc
  "Given the full source text of a clj/cljs/cljc file, returns the ns docstring if present.
   Handles both string doc right after the ns name and ^{:doc \"...\"} metadata.
   Safe for cljc via :read-cond :allow."
  [source]
  (let [reader (rrt/indexing-push-back-reader source)
        eof    ::eof
        opts   {:read-cond :allow
                :features  #{:clj :cljs}
                :eof       eof}]
    (loop [form (tr/read opts reader)]
      (cond
        (= form eof) nil
        (and (seq? form) (= 'ns (first form)))
        (let [[_ ns-sym & more] form
              x1        (first more)
              ;; ns can be: (ns foo "doc" ...) or (ns ^{:doc "..."} foo ...)
              has-meta? (map? x1)
              m         (when has-meta? x1)
              rest*     (if has-meta? (next more) more)
              x2        (first rest*)]
          (or (when (string? x1) x1)
              (get m :doc)
              (when (string? x2) x2)))
        :else (recur (tr/read opts reader))))))

(defn read-file
  "Reads a file and annotates it with:
   - :kind     (:code or :text)
   - :ext      (extension without dot, lowercase)
   - :metadata (optional, human-friendly string)
   If metadata is nil and this is a Clojure source file, tries to pull the ns docstring."
  ([^File f] (read-file f nil))
  ([^File f metadata]
   (let [ext     (hf/file-ext f)
         kind    (if (contains? text-exts ext) :text :code)
         content (slurp f)
         md      (or metadata
                     (when (and (= kind :code) (contains? code-exts ext))
                       (try
                         (some-> (extract-ns-doc content)
                                 str/trim
                                 (not-empty))
                         (catch Throwable _ nil))))]
     {:filename (.getName f)
      :ext      ext
      :kind     kind
      :metadata md
      :content  content})))

;
;(def text-exts
;  "Extensions considered 'text' (no code fences). Extend as needed."
;  #{"md" "txt"})
;
;(defn read-file
;  "Reads a file and annotates it with:
;   - :kind   (:code or :text)
;   - :ext    (extension without dot, lowercase)
;   - :metadata (optional, human-friendly string)
;   Arity 1 keeps backward compatibility; arity 2 accepts metadata."
;  ([^File f] (read-file f nil))
;  ([^File f metadata]
;   (let [ext (hf/file-ext f)
;         kind (if (contains? text-exts ext) :text :code)]
;     {:filename (.getName f)
;      :ext      ext
;      :kind     kind
;      :metadata metadata
;      :content  (slurp f)})))

;; --- main flows ---

(defn read-files-in-dir
  "Step 1: From a folder -> seq of enriched file maps (up to (map read-file)).
   Defaults to [\"*.md\"]."
  ([dir]
   (read-files-in-dir dir ["*.md"]))
  ([dir patterns]
   (->> (hf/files-matching-patterns dir patterns)
        (map read-file)))) ;; prevent dupes when patterns overlap


(defn- normalize-pattern-entries
  "Takes a seq of entries where each entry is either:
     - string pattern, or
     - map {:pattern <string> :metadata <string>}
   -> returns a seq of {:pattern <string> :metadata <string|nil>}."
  [entries]
  (map (fn [e]
         (cond
           (string? e) {:pattern e :metadata nil}
           (and (map? e) (contains? e :pattern)) {:pattern (:pattern e) :metadata (:metadata e)}
           :else (throw (ex-info "Invalid pattern entry; expected string or {:pattern :metadata}"
                                 {:entry e}))))
       entries))

(defn- expand-pattern-entry->files
  "For a single normalized pattern entry, returns a seq of {:file <File> :metadata <string|nil>}."
  [{:keys [pattern metadata]}]
  (let [matched (hf/files-matching-path-patterns [pattern])]
    (map (fn [^File f] {:file f :metadata metadata}) matched)))

(defn read-files-by-patterns
  "Pattern-first API: entries may be strings or maps {:pattern :metadata}.
   Returns seq of enriched file maps including optional :metadata."
  [entries]
  (let [norm (normalize-pattern-entries entries)]
    (->> norm
         (mapcat expand-pattern-entry->files)
         ;; If multiple patterns hit the same file, keep the first metadata found.
         (reduce (fn [acc {:keys [file metadata]}]
                   (let [k (.getCanonicalPath ^File file)]
                     (if (contains? acc k)
                       acc
                       (assoc acc k (read-file file metadata)))))
                 {})
         vals)))

;; ---------- UPDATED: aggregate-md now renders :metadata if present ----------

(defn aggregate-md
  "From seq of file maps -> aggregated Markdown string.
   Expects {:filename :ext :kind :content [:metadata optional]}."
  [file-maps]
  (->> file-maps
       (map (fn [{:keys [filename content kind ext metadata]}]
              (let [meta-block (when (some? metadata)
                                 (str "_" metadata "_\n\n"))]
                (if (= kind :code)
                  (format "## %s\n\n%s```%s\n%s\n```\n\n"
                          filename (or meta-block "") (or ext "") content)
                  (format "## %s\n\n%s%s\n\n"
                          filename (or meta-block "") content)))))
       (apply str)))

(defn aggregate-md-from-patterns
  "Glue: entries (strings or {:pattern :metadata}) -> aggregated Markdown."
  [entries]
  (let [before (read-files-by-patterns entries)]
    (aggregate-md before)))


(comment
  ;; 1 CLJ file + 2 MD files in one shot (exact paths)
  (aggregate-md-from-patterns
    [{:pattern "src/core.clj" :metadata "Code for the logic"}
     {:pattern "docs/intro.md" :metadata "Output of the code"}
     "docs/guide.md"])

  ;; Using globs with a description, mixed with a single exact file:
  (aggregate-md-from-patterns
    [{:pattern "src/**/*.clj" :metadata "All Clojure sources"}
     {:pattern "docs/*.md" :metadata "User-facing docs"}
     "README.md"])

  )
