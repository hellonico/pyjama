(ns morning.patterns-test
  (:require [clojure.test :refer :all]
            [pyjama.helpers.file :as hf])
  (:import (java.io File)
           (java.nio.file FileSystems Paths PathMatcher)))

(deftest table-driven-pattern-counts
  (let [cases [
               ;{:pattern "/Users/nico/cool/origami-nightweave/pyjama-slides/src/**/*.clj"       :count 4}
               ;{:pattern "src/**/analysis/*.clj" :count 3}
               ;{:pattern "docs/*.md"    :count 2}
               ;{:pattern "src/margin_mania/reporting/compare_reviews.clj"    :count 1}
               ;{:pattern "resources/reporting/*.edn"      :count 3}
               {:pattern "src/**/core.clj"       :count 8}
               ]
        ;; Real PathMatchers, just applied to our fake Paths
        mk-matchers (fn [patterns]
                      (mapv #(.getPathMatcher (FileSystems/getDefault)
                                              (str "glob:" %))
                            patterns))]
    (with-redefs [;; Pretend the project tree is exactly our fake files
                  ;file-seq (fn [_] (map fake-file fake-paths))
                  ;; Use real glob semantics for the given patterns
                  hf/path-matchers mk-matchers
                  ;; Make sure "." resolves to a relative Path so relativize works
                  ;; (this mirrors normal behavior and needs no override for io/file)
                  ]
      (doseq [{:keys [pattern count]} cases]
        (testing (str "pattern: " pattern)
          (let [matches (hf/files-matching-path-patterns [pattern])]
            (is (= count (clojure.core/count matches))
                (str "Got: " (map #(.getPath ^File %) matches)))))))))

