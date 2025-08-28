(ns pyjama.helpers.file
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.io File PushbackReader)
           (java.nio.file FileSystems PathMatcher)))


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
  "Returns the lowercase extension without the leading dot, or nil."
  [^File f]
  (some-> (.getName f)
          (str/lower-case)
          (str/split #"\.")
          last))


;; ---------- PATH PATTERN (glob) SUPPORT ----------

(defn path-matchers
  "Build PathMatcher fns from glob patterns (relative to project root)."
  [patterns]
  (let [fs (FileSystems/getDefault)]
    (map #(.getPathMatcher fs (str "glob:" %)) patterns)))

(defn files-matching-path-patterns
  "Return seq of Files matching any of the given *path* glob patterns.
   Patterns are matched against the file's path *relative to the current dir*.
   Examples: [\"src/**/*.clj\" \"docs/*.md\" \"README.md\"]"
  [patterns]
  (let [matchers (path-matchers patterns)
        root (.toPath (io/file "."))]
    (->> (file-seq (io/file "."))
         (filter #(.isFile ^File %))
         (filter (fn [^File f]
                   (let [rel (.relativize root (.toPath f))]
                     (some #(.matches ^PathMatcher % rel) matchers))))
         distinct)))

;
;
;

(defn load-config [cfg]
  (cond
    (string? cfg)
    (with-open [r (io/reader cfg)]
      (edn/read (PushbackReader. r)))

    (map? cfg) cfg

    :else (throw (ex-info "Unsupported config type" {:given cfg}))))
