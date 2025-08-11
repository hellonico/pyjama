(ns secrets.core
 (:require [clojure.edn :as edn]
           [clojure.string :as str]
           [clojure.java.io :as io])
 (:import [java.io PushbackReader]))

(defn- load-secrets-file [path]
 (let [file (io/file path)]
  (when (.exists file)
   (with-open [r (PushbackReader. (io/reader file))]
    (edn/read r)))))

(def ^:private secrets
 ;; Merge priority: local file > home file
 (merge
  (load-secrets-file "secrets.edn")
  (load-secrets-file (str (System/getProperty "user.home") "/secrets.edn"))))

(defn get-secret
 "Get a secret by keyword.
  Priority: ./secrets.edn > ~/secrets.edn > ENV var."
 [k]
 (or
  (get secrets k)
  (System/getenv (-> k name str/upper-case (str/replace "-" "_")))))