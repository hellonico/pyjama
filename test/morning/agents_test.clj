(ns morning.agents-test
  (:require [clojure.test :refer [deftest]]
            [pyjama.agents :as a]))

(deftest simple-agents-system
  (let [broker (a/create-broker)]
    (let [agent-1 (a/make-echo-agent "echo" broker {} nil)
          agent-2 (a/make-logger-agent "log" broker {} nil)]

      ((:subscribe broker) :trip-request-in (:input-chan agent-1))
      ((:forward broker) (:output-chan agent-1) :trip-request-out)
      ((:subscribe broker) :trip-request-out (:input-chan agent-2))

      )

    ((:publish broker) :trip-request-in {:from "Paris" :to "Tokyo" :date "2025-02-10"})
    (Thread/sleep 5000)))