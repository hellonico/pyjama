(ns morning.state_test
  (:require
    [clojure.test :refer :all]
    [pyjama.models :refer :all]
    [pyjama.state :refer :all]))

(deftest fetch-and-sort
  (let [models (fetch-remote-models)]
    (clojure.pprint/pprint (filter-models models "mistral"))
    (clojure.pprint/pprint (sort-models models :name :asc))
    (clojure.pprint/pprint (sort-models models :pulls :desc))))

(def url (or (System/getenv "OLLAMA_URL") "http://localhost:11434"))

(deftest connection-test
  (let [state (atom {:url url})]
    ;(local-models state)
    ;(remote-models state)
    (check-connection state)
    (Thread/sleep 5000)
    (clojure.pprint/pprint @state)))

(deftest update-models-test
  (let [state (atom {:url url})]
    (local-models state)
    ;(remote-models state)
    (clojure.pprint/pprint @state)))

(defn -get-sizes[model]
  (let [state (atom {:url url})]
    (remote-models state)
    (clojure.pprint/pprint (get-sizes (:models @state) model))))
(deftest get-sizes-test
  ;(-get-sizes "deepseek-v2.5")
  ;(-get-sizes "deepseek-coder-v2")
  (-get-sizes "phi4")
  )


(deftest get-installed-sizes-test
  (let [state (atom {:url url})
        model-name "llama3.2"
        ;model-name "deepseek-coder-v2"
        ]
    (local-models state)
    (-> (get-installed-sizes (:local-models @state) model-name)
        (clojure.pprint/pprint))))

(deftest request
  (let [state (atom {:response "" :url url :model "llama3.2" :prompt "Why is the sky blue?"})]
    (handle-submit state)
    (Thread/sleep 5000)
    (clojure.pprint/pprint @state)))

(deftest chat
  (let [state (atom {:response "" :url url :model "llama3.2" :messages [{:role :user :content "Who is mario?"}]})]
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