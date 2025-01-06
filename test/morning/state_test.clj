(ns morning.state_test
  (:require
    [pyjama.models :refer :all]
    [pyjama.state :refer :all]
    [clojure.test :refer :all]))

(deftest fetch-and-sort
  (let [models (fetch-remote-models)]
    (clojure.pprint/pprint (filter-models models "mistral"))
    (clojure.pprint/pprint (sort-models models :name :asc))
    (clojure.pprint/pprint (sort-models models :pulls :desc))))

(def url (or (System/getenv "OLLAMA_URL") "http://localhost:11434"))

(deftest update-state-test
  (let [ state (atom {:url url})]
    (local-models state)
    (remote-models state)
    (clojure.pprint/pprint @state)))

(deftest request
  (let [state (atom {:response "" :url url :model "llama3.2" :prompt "Why is the sky blue?"})]
    (handle-submit state)
    (Thread/sleep 5000)
    (clojure.pprint/pprint @state)))

(deftest chat
  (let [state (atom {:response "" :url url :model "llama3.2" :messages [{:role :user :content "Who is mario?"}] })]
    (handle-chat state)
    (while (:processing @state)
      (Thread/sleep 1000))
    (clojure.pprint/pprint @state)))

(deftest request-and-stop
  (let [state (atom {:response "" :url url :model "llama3.2" :prompt "Why is the sky blue?"})]
    (handle-submit state)
    (Thread/sleep 1500)
    (swap! state assoc :processing false)
    (clojure.pprint/pprint @state)))

(deftest pull-model-stream-test
  (let [state (atom {:url url})]
    (pull-model-stream state "llama3.2")
    (Thread/sleep 5000)
    (clojure.pprint/pprint @state)))