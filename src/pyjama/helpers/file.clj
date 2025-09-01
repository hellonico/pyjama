(ns pyjama.helpers.file
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.io File)
           (java.nio.file FileSystems PathMatcher Paths)))

(defn sanitize-filename [s]
  (-> (or s "output")
      (str/replace #"[^\p{Alnum}\.\-]+" "_")
      (str/replace #"_{2,}" "_")
      (str/replace #"^_|_$" "")))

(defn ensure-parent-dirs! [^File f]
  (when-let [p (.getParentFile f)]
    (.mkdirs p))
  f)


;; --- pattern helpers ---

(defn glob->regex
  "Convert a simple glob pattern like *.md or foo*.clj into a regex.
   Supports * as a wildcard. Anchors to full string match."
  [glob]
  (-> glob
      (str/replace "." "\\.")                               ;; escape literal dots
      (str/replace "*" ".*")                                ;; translate * to .*
      (format "^%s$")                                       ;; anchor
      re-pattern))

(defn- matches-any? [filename patterns]
  (some #(re-matches % filename) patterns))

;; --- file discovery ---

(defn files-matching-patterns
  "Return seq of java.io.File under folder matching given filename glob patterns."
  ([folder patterns]
   (let [regexps (map glob->regex patterns)]
     (->> folder
          io/file
          file-seq
          (filter #(and (.isFile ^File %)
                        (matches-any? (.getName ^File %) regexps))))))
  ([folder]
   (files-matching-patterns folder ["*.md"])))

(defn file-ext
  "Returns the lowercase extension without the leading dot, or nil.
   Only considers a real dot in the basename (ignores dotfiles like `.env`)."
  [^File f]
  (let [name (.getName f)
        i    (.lastIndexOf name ".")]
    (when (pos? i)                         ; i > 0 → there is a dot not at start
      (-> name (subs (inc i)) str/lower-case))))


;; ---------- PATH PATTERN (glob) SUPPORT ----------

(defn path-matchers
  "Build PathMatcher fns from glob patterns (relative to project root)."
  [patterns]
  (let [fs (FileSystems/getDefault)]
    (map #(.getPathMatcher fs (str "glob:" %)) patterns)))


(defn distinct-by
  "Returns a lazy sequence of the elements of coll with duplicates removed,
   comparing on (f x)."
  [f coll]
  (let [seen (java.util.HashSet.)]
    (remove
      (fn [x]
        (let [k (f x)]
          (if (.contains seen k)
            true
            (do (.add seen k) false))))
      coll)))

(defn- first-glob-index
  "Return the index of the first glob metachar in s, or nil if none."
  ^Long [^String s]
  (let [idxs (remove neg? [(.indexOf s "*")
                           (.indexOf s "?")
                           (.indexOf s "[")
                           (.indexOf s "{")])]
    (when (seq idxs) (long (apply min idxs)))))

(defn- last-sep-before
  "Find the last path separator before index `i` in string `s`.
   Checks both '/' and '\\' to be cross-platform."
  ^Long [^String s ^long i]
  (let [from (dec (long i))                                   ; lastIndexOf is inclusive, so use (dec i)
        from (if (neg? from) -1 from)
        a (.lastIndexOf s (int \/) from)
        b (.lastIndexOf s (int \\) from)]
    (max a b)))

(defn- pattern->root+rel
  "Split a glob PATTERN into:
   - root: directory to start searching (absolute Path)
   - rel:  glob to apply relative to root
   Examples:
     \"src/**/*.clj\"        => root: \"src\",           rel: \"**/*.clj\"
     \"/opt/app/docs/*.md\"  => root: \"/opt/app/docs\", rel: \"*.md\"
     \"README.md\"           => root: \".\",            rel: \"README.md\""
  [^String pattern]
  (let [p         (.. (Paths/get pattern (make-array String 0)) normalize toString)
        gi        (long (or (first-glob-index p) (count p)))
        last-sep  (last-sep-before p gi)
        root-str  (if (neg? last-sep) "." (.substring p 0 last-sep))
        rel-start (if (neg? last-sep) 0 (inc last-sep))
        rel       (.substring p rel-start)]
    {:root (.normalize (.toAbsolutePath (Paths/get root-str (make-array String 0))))
     :rel  (if (empty? rel) "**" rel)}))

(defn- matcher-for-rel
  "Create a PathMatcher for a *relative* glob."
  ^PathMatcher [^String rel-glob]
  (.getPathMatcher (FileSystems/getDefault) (str "glob:" rel-glob)))

(defn files-matching-path-patterns
  "Return seq of java.io.File for files matching any of the given *path* glob patterns.
   Patterns may be relative to the current dir or rooted elsewhere (relative or absolute).
   Matching is done against each file's path relative to the inferred root of its pattern."
  [patterns]
  (let [specs (for [p patterns
                    :let [{:keys [root rel]} (pattern->root+rel p)]]
                {:root root
                 :matcher (matcher-for-rel rel)})]
    (->> specs
         (mapcat
           (fn [{:keys [^java.nio.file.Path root ^PathMatcher matcher]}]
             (let [root-file (.toFile root)]
               (when (.exists root-file)
                 (for [^File f (file-seq root-file)
                       :when (.isFile f)
                       :let [rel (.relativize root (.toPath f))]
                       :when (.matches matcher rel)]
                   f)))))
         (remove nil?)
         (distinct-by #(.getCanonicalPath ^File %)))))  ; de-dupe across overlapping patterns
