(ns morning.gen-test
  (:require [clojure.test :refer :all]
            [pyjama.functions  ;; brings in airport-code-generator etc.
             :refer :all]
            ))

(deftest airport-test
;; now you can just do:
(airport-code-generator "Paris"))