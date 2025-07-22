(ns morning.pipeline-test
 (:require
  [cheshire.core :as json]
  [pyjama.core :as core]
  [clojure.test :refer :all]))


(System/setProperty "agents.edn" "test-resources/agents.edn")

(deftest test-single-call
 (testing "chain-ed calls works"
  (let [result
   (core/call {:id :pp :prompt "what is the meaning of AI?"})]
   (println (:why result)))))
