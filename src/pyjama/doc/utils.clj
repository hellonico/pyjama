(ns pyjama.doc.utils
  "Utilities for turning file patterns into aggregated Markdown used by pyjama.doc.core.

   - Expands path/glob patterns (optionally with per-pattern :metadata) to files, de-duplicating overlaps.
   - Reads each file and annotates it with :kind, :ext, and optional :metadata; for Clojure sources,
     attempts to extract the namespace docstring safely via clojure.tools.reader with :read-cond :allow.
   - Produces aggregated Markdown with code fences for code files and an italic metadata preamble.
   - Main entry points: read-files-by-patterns, aggregate-md, aggregate-md-from-patterns."
  (:require [clojure.string :as str]
            [clojure.tools.reader :as tr]
            [clojure.tools.reader.reader-types :as rrt]
            [pyjama.helpers.file :as hf])
  (:import (java.io File)
           (java.nio.charset StandardCharsets)
           (java.nio.file Files OpenOption Path StandardOpenOption)))

;; Consider expanding these via config if needed.
(def text-exts #{"md" "txt" "rst" "adoc"})
;(def code-exts #{"clj" "cljc" "cljs" "edn" "java" "scala" "kt" "py" "rb" "js" "ts" "tsx" "json" "yaml" "yml" "sh" "bash" "zsh" "go" "rs" "c" "h" "cpp" "hpp" "cs" "swift"})

(def ^:private ext->fence
  {"clj" "clojure" "cljc" "clojure" "cljs" "clojure"
   "edn" "clojure"
   "js" "javascript" "ts" "typescript" "tsx" "tsx"
   "json" "json" "yaml" "yaml" "yml" "yaml"
   "sh" "bash" "bash" "bash" "zsh" "bash"
   "rb" "ruby" "py" "python" "java" "java" "kt" "kotlin"
   "go" "go" "rs" "rust" "c" "c" "h" "c" "cpp" "cpp" "hpp" "cpp"
   "cs" "csharp" "swift" "swift"})

(defn- fence-lang [ext]
  (or (ext->fence (str/lower-case (or ext "")))
      (str/lower-case (or ext ""))))

(defn- extract-ns-doc
  "Given source text of a clj/cljs/cljc file, returns the ns docstring if present.
   Handles both string doc right after ns name and ^{:doc \"...\"}."
  [source]
  (let [reader (rrt/indexing-push-back-reader source)
        eof ::eof
        opts {:read-cond :allow
              :features  #{:clj :cljs}
              :eof       eof}]
    (loop [form (try (tr/read opts reader)
                     (catch Throwable _ eof))]
      (cond
        (= form eof) nil
        (and (seq? form) (= 'ns (first form)))
        (let [[_ _ns-sym & more] form
              x1 (first more)
              has-meta? (map? x1)
              m (when has-meta? x1)
              rest* (if has-meta? (next more) more)
              x2 (first rest*)]
          (some-> (or (when (string? x1) x1)
                      (get m :doc)
                      (when (string? x2) x2))
                  str/trim
                  not-empty))
        :else (recur (try (tr/read opts reader)
                          (catch Throwable _ eof)))))))

  (defn read-file
    "Reads a file and annotates it with:
     - :kind     (:code or :text)
     - :ext      extension (lowercase, no dot)
     - :metadata (optional, human-friendly string). If nil and Clojure source, tries ns doc.
     - :content  file content as UTF-8 string
     Options:
     - metadata (string|nil)
     - :max-bytes (when set, skip reading content if file exceeds this size; adds :skipped? true)"
    ([^File f] (read-file f nil))
    ([^File f metadata] (read-file f metadata {:max-bytes nil}))
    ([^File f metadata {:keys [max-bytes]}]
     (let [ext (hf/file-ext f)
           kind (if (contains? text-exts (str/lower-case (or ext ""))) :text :code)
           size (.length f)]
       (if (and max-bytes (number? max-bytes) (> size max-bytes))
         {:filename (.getName f)
          :ext      ext
          :kind     kind
          :metadata metadata
          :content  (format "[[skipped: file too large (%d bytes)]]" size)
          :skipped? true}
         (let [content (slurp f :encoding "UTF-8")
               md (or metadata
                      (when (and (= kind :code) (contains? #{"clj" "cljc" "cljs"} ext))
                        (try
                          (extract-ns-doc content)
                          (catch Throwable _ nil))))]
           {:filename (.getName f)
            :ext      ext
            :kind     kind
            :metadata (some-> md str/trim not-empty)
            :content  content})))))

  ;; --- main flows ---

  (defn read-files-in-dir
    "From a folder -> seq of enriched file maps matching patterns (defaults to [\"*.md\"])."
    ([dir]
     (read-files-in-dir dir ["*.md"]))
    ([dir patterns]
     (->> (hf/files-matching-patterns dir patterns)
          (map #(read-file ^File % nil))
          ;; Optional: stable sort
          (sort-by #(-> ^File (:file %) .getCanonicalPath)))))

  (defn- normalize-pattern-entries
    "Each entry is either a string pattern or {:pattern <string> :metadata <string|nil>}.
     Returns seq of {:pattern <string> :metadata <string|nil>}."
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
  ;
  ;(defn read-files-by-patterns
  ;  "Pattern-first API: entries may be strings or maps {:pattern :metadata}.
  ;   Returns a stable seq of enriched file maps (dedup by canonical path, keep first metadata)."
  ;  [entries]
  ;  (let [norm (normalize-pattern-entries entries)
  ;        by-path (reduce
  ;                  (fn [acc {:keys [file metadata]}]
  ;                    (let [k (.getCanonicalPath ^File file)]
  ;                      (if (contains? acc k)
  ;                        acc
  ;                        (assoc acc k (read-file file metadata)))))
  ;                  {}
  ;                  (mapcat expand-pattern-entry->files norm))]
  ;    ;; stable ordering by canonical path for deterministic output
  ;    (->> by-path
  ;         (into [])
  ;         (sort-by key)
  ;         (mapv val))))

;; --- helpers ---------------------------------------------------------------

(def ^:private glob-chars #{"*" "?" "[" "]" "{" "}"})
(defn- glob-pattern? [s]
  (some #(str/includes? s %) glob-chars))
;
;(defn- existing-file? [s]
;  (try
;    (let [f (File. s)]
;      (and (.exists f) (.isFile f)))
;    (catch Exception _ false)))

(defn- ensure-md-temp-file! [text]
  ;; Create a temp .md file with the inline markdown content and return its absolute path.
  (let [p (Files/createTempFile "inline-" ".md" (make-array java.nio.file.attribute.FileAttribute 0))
        bytes (.getBytes (str text \newline) StandardCharsets/UTF_8)]
    (^[Path byte/1 OpenOption/1] Files/write p bytes
                                             (into-array StandardOpenOption
                                                         [StandardOpenOption/WRITE
                              StandardOpenOption/TRUNCATE_EXISTING]))
    (-> p .toAbsolutePath str)))



;; Heuristic: signal "this is markdown text", not a path.
(def ^:private markdown-signal-regexes
  [#"(?m)^\s{0,3}#\s"          ;; headings
   #"(?m)^\s{0,3}(```|~~~)"    ;; code fences
   #"(?m)^\s{0,3}(\*|-|\+)\s"  ;; bullet lists
   #"(?m)^\s{0,3}\d+\.\s"      ;; ordered lists
   #"(?m)^\s{0,3}>\s"          ;; blockquotes
   #"\[[^\]]+\]\([^)]+\)"      ;; links [text](url)
   #"(?m)^---\s*$"             ;; hr/frontmatter fence
   #"\|\s*[^|\n]+\s*\|"        ;; table row
   ])

(defn- looks-like-markdown? ^Boolean [^String s]
  (or (str/includes? s "\n")
      (some #(re-find % s) markdown-signal-regexes)
      (and (<= 40 (count s) 4096)     ;; short paths rarely have many spaces; texts do
           (re-find #"\s" s))))

(defn- maybe-inline->temp [s]
  ;; NEW: only inline when the content looks like markdown; otherwise leave it alone.
  (if (looks-like-markdown? s)
    (ensure-md-temp-file! s)
    s))


(defn- preprocess-inline-markdown
  "Walks entries; if an entry is a string (or a map's :pattern) that is not a file path
   and not a glob, writes it to a temp .md file and substitutes the temp path in place."
  [entries]
  (mapv
    (fn [e]
      (cond
        (string? e)
        (maybe-inline->temp e)

        (map? e)
        (let [p (:pattern e)]
          (if (string? p)
            (assoc e :pattern (maybe-inline->temp p))
            e))

        :else e))
    entries))

;; --- your existing function with the pre-processing added ------------------

(defn read-files-by-patterns
  "Pattern-first API: entries may be strings or maps {:pattern :metadata}.
   Returns a stable seq of enriched file maps (dedup by canonical path, keep first metadata).
   Pre-processing: inline markdown strings are written to temp .md files and used as patterns."
  [entries]
  (let [entries* (preprocess-inline-markdown entries)
        norm (normalize-pattern-entries entries*)
        by-path (reduce
                  (fn [acc {:keys [file metadata]}]
                    (let [k (.getCanonicalPath ^File file)]
                      (if (contains? acc k)
                        acc
                        (assoc acc k (read-file file metadata)))))
                  {}
                  (mapcat expand-pattern-entry->files norm))]
    ;; stable ordering by canonical path for deterministic output
    (->> by-path
         (into [])
         (sort-by key)
         (mapv val))))

  ;(defn aggregate-md
  ;  "From seq of file maps -> aggregated Markdown string.
  ;   Expects {:filename :ext :kind :content [:metadata optional]}.
  ;   Adds code fences for code files and italic metadata preamble."
  ;  [file-maps]
  ;  (->> file-maps
  ;       (map (fn [{:keys [filename content kind ext metadata]}]
  ;              (let [meta-block (when (some? metadata)
  ;                                 (str "_" metadata "_\n\n"))]
  ;                (if (= kind :code)
  ;                  (format "## %s\n\n%s```%s\n%s\n```\n\n"
  ;                          filename (or meta-block "") (fence-lang ext) content)
  ;                  (format "## %s\n\n%s%s\n\n"
  ;                          filename (or meta-block "") content)))))
  ;       (apply str)))
(def ^:private comment-prefix
  {"clj" ";;" "cljs" ";;" "cljc" ";;"
   "edn" ";;"
   "py"  "#"
   "rb"  "#"
   "js"  "//" "ts" "//"
   "java" "//"
   "go" "//"
   "c"   "//" "h" "//"
   "cpp" "//" "hpp" "//"
   "sh"  "#" "bash" "#"
   "yaml" "#" "yml" "#" "toml" "#" "ini" "#"
   "html" "<!--" "css" "/*" "sql" "--"})

(defn- file-comment-line [ext filename]
  (let [pfx (get comment-prefix ext "#")
        sfx (cond
              (= pfx "/*") " */"
              (= pfx "<!--") " -->"
              :else "")]
    (format "%s file:%s%s" pfx filename sfx)))

(defn aggregate-md
  "From seq of file maps -> aggregated Markdown string.
   Expects {:filename :ext :kind :content [:metadata optional]}.
   Adds code fences for code files and italic metadata preamble.
   For code files, embeds filename as a comment at the top."
  [file-maps]
  (->> file-maps
       (map (fn [{:keys [filename content kind ext metadata]}]
              (let [meta-block (when (some? metadata)
                                 (str "_" metadata "_\n\n"))]
                (if (= kind :code)
                  (format "%s```%s\n%s\n%s\n```\n\n"
                          (or meta-block "")
                          (fence-lang ext)
                          (file-comment-line ext filename)
                          content)
                  (format "## %s\n\n%s%s\n\n"
                          filename (or meta-block "") content)))))
       (apply str)))



  (defn aggregate-md-from-patterns
    "entries (strings or {:pattern :metadata}) -> aggregated Markdown."
    [entries]
    (aggregate-md (read-files-by-patterns entries)))

  (comment
    ;; Examples
    (aggregate-md-from-patterns
      [{:pattern "src/core.clj" :metadata "Code for the logic"}
       {:pattern "docs/intro.md" :metadata "Output of the code"}
       "docs/guide.md"])

    (aggregate-md-from-patterns
      [{:pattern "src/**/*.clj" :metadata "All Clojure sources"}
       {:pattern "docs/*.md" :metadata "User-facing docs"}
       "README.md"])

    (aggregate-md-from-patterns
      [{:pattern "src/**/*.clj"}
       {:pattern "docs/*.md"}
       "README.md"])
    )