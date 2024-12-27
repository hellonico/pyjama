(ns morning.models_test
  (:require
    [pyjama.models :refer :all]
    [pyjama.state :refer :all]
    [clojure.test :refer :all]))

(deftest fetch-and-sort
  (let [models (fetch-remote-models)]
    (clojure.pprint/pprint (filter-models models "mistral"))
    (clojure.pprint/pprint (sort-models models :name :asc))
    (clojure.pprint/pprint (sort-models models :pulls :desc))
    ))

(deftest update-state-test
  (let [ state (atom {:url "http://localhost:11434"})]
    (local-models state)
    (remote-models state)
    (clojure.pprint/pprint @state)))

(deftest request
  (let [state (atom {:response "" :url "http://localhost:11434" :model "llama3.2" :prompt "Why is the sky blue?"})]
    (handle-submit state)
    (Thread/sleep 5000)
    (clojure.pprint/pprint @state)
    ))