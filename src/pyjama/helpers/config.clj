(ns pyjama.helpers.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.io PushbackReader)))

(defn ^:private deep-merge
  "Recursively merge maps; rightmost value wins on non-maps."
  ([] {})
  ([a] a)
  ([a b]
   (cond
     (and (map? a) (map? b)) (merge-with deep-merge a b)
     :else b)))

(defn ^:private read-edn-file [path]
  (with-open [r (io/reader path)]
    ;; {:eof nil} lets us handle empty files gracefully as nil => {}
    (or (edn/read {:eof nil} (PushbackReader. r)) {})))

(defn ^:private ->sources
  "Normalize cfg into a seq of sources (strings and/or maps) in left→right order."
  [cfg]
  (cond
    ;; comma-separated paths
    (string? cfg)
    (->> (str/split cfg #",")
         (map str/trim)
         (remove str/blank?))

    ;; list/vector of items (strings and/or maps)
    (sequential? cfg) cfg

    ;; single map
    (map? cfg) [cfg]

    :else
    (throw (ex-info "Unsupported config type" {:given cfg}))))

(defn load-config
  "Load and deep-merge configuration from:
   - a comma-separated string of EDN file paths,
   - a sequence of paths/maps,
   - a single map or single path.
   Merges left→right; later items override earlier ones."
  [cfg]
  (reduce
    (fn [acc src]
      (let [m (cond
                (string? src) (read-edn-file src)
                (map? src)    src
                :else (throw (ex-info "Unsupported item in config sequence" {:item src})))]
        (deep-merge acc m)))
    {}
    (->sources cfg)))
