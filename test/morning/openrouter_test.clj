(ns morning.openrouter-test
  (:require
    [pyjama.openrouter.core :as openrouter]
    [clojure.test :refer :all]))

(deftest claude-write-fibonacci
  (let [response (openrouter/with-prompt "Write Fibonacci in Clojure!")]
    (println
      (->
        response
        :choices first :message :content))))