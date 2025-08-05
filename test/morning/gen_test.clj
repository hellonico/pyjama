(ns morning.gen-test
  (:require [clojure.test :refer :all]
            [pyjama.functions  ;; brings in airport-code-generator etc.
             :refer :all]))

(defollama-from-edn "functions.edn")

(deftest airport-test
;; now you can just do:
(airport-code-generator "Paris"))


;; At test-runtime, generate and define a function called `reverse-string`
;; that takes a string and returns it reversed.
(define-generated-fn
 clojure-code-generator    ;; the generator fn from your EDN loader
 'reverse-string           ;; symbol you want to define
 "reverses its input string s")  ;; human-readable description

(deftest reverse-string-test
 (testing "reverse-string should invert characters"
  (let [input  "hello"
        output (reverse-string input)]
   (println "reverse-string of" input "→" output)
   (is (= "olleh" (apply str output))))))