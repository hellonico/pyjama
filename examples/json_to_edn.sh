#!/bin/sh
#_(
  DEPS='
  {
      :deps {
    cheshire/cheshire       {:mvn/version "5.10.0"}
    }}
   '

exec clj $OPTS -Sdeps "$DEPS" -M "$0" "$@"

)

(ns json-to-edn
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.pprint]
            [clojure.java.io :as io]))

(defn json-file-to-edn
  "Converts a JSON file to an EDN representation."
  [json-file-path]
  (with-open [reader (io/reader json-file-path)]
    (let [json-data (json/parse-stream reader true)]
      (clojure.pprint/pprint json-data))))

;; Example usage
(def json-file-path (first *command-line-args*)) ;; Replace with your JSON file path
(println (json-file-to-edn json-file-path))

