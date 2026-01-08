(ns examples.gemini
  (:require [pyjama.core :as pyjama]))

(defn -main []
  (println "Testing Gemini API...")
  (try
    (let [response (pyjama/call {:impl :gemini
                                 :model "gemini-1.5-flash"
                                 :prompt "Tell me a one-sentence joke."})]
      (println "Response:" response))
    (catch Exception e
      (println "Test failed:" (.getMessage e)))))
