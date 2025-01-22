(ns morning.utils
  (:require
    [pyjama.core]
    [clojure.test :refer :all]))


(deftest get-templated-prompt
  (->
    {:template "Explain in three points: %s" :prompt "Super Mario"}
    pyjama.core/templated-prompt
    println))