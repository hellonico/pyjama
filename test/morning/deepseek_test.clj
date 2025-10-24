(ns morning.deepseek-test
  (:require
    [clojure.test :refer :all]
    [pyjama.core :as pyjama]
    [pyjama.deepseek.core :as deep]))



(deftest deep-first-call
  (deep/call
    {:prompt "write fibonacci in Clojure" :model "deepseek-chat"}))


(deftest deep-second-call
  (pyjama/call
    {:prompt "write fibonacci in Clojure" :impl :deepseek :model "deepseek-chat"}))