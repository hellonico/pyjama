(ns morning.model-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [pyjama.core]
    [pyjama.models :as m]
    ))

(def URL (or (System/getenv "OLLAMA_URL") "http://localhost:11434"))



(deftest list-models-names-00
  (-> URL
      (m/local-models)
      (clojure.pprint/pprint)))

(deftest list-models-names-01
  (-> URL
      (m/local-models "deepseek")
      (clojure.pprint/pprint)))

(deftest list-models-names-02
  (-> URL
      (m/local-models ["deepseek" "tiny"])
      (clojure.pprint/pprint)))


(deftest list-models-names-stripped-00
  (-> URL
      (m/local-models-strip-latest)
      (clojure.pprint/pprint)))

(deftest list-models-names-stripped-01
  (-> URL
      (m/local-models-strip-latest "deepseek")
      (clojure.pprint/pprint)))

(deftest list-models-names-stripped-02
  (-> URL
      (m/local-models-strip-latest ["deepseek" "tiny"])
      (clojure.pprint/pprint)))