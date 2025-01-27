(ns morning.parallel-test
  (:require [clojure.pprint]
            [clojure.test :refer :all]
            [pyjama.parallel :refer :all])
  )

(def ollama-url (or (System/getenv "OLLAMA_URL") "http://localhost:11434"))
(deftest tinyllama-and-blue
  (let [app-state (atom {:url ollama-url :tasks {}})]
    ;(println "Starting parallel tasks...")
    (parallel-generate
      app-state
      {
       :models  ["tinyllama"]
       :pre     "Explain in three points the following sentence:\n %s"
       :prompts ["Why is the sky blue"]}
      identity
      (fn [] (clojure.pprint/pprint @app-state)))))