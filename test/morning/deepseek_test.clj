(ns morning.deepseek-test
  (:require
    [clojure.test :refer :all]
    [pyjama.deepseek.core :as deep]))



(deftest deep-first-call
  (deep/call
    {:prompt "write fibonacci in Clojure" :model "deepseek-chat"}))