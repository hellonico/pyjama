(ns morning.parallel-test
  (:require [clojure.pprint]
            [clojure.test :refer :all]
            [pyjama.parallel :refer :all]))

(def ollama-url (or (System/getenv "OLLAMA_URL") "http://localhost:11434"))
(deftest tinyllama-and-blue
  (let [processing (atom true)]
    (let [app-state (atom {:url ollama-url :tasks {}})]
      (parallel-generate
        app-state
        {
         :models  ["tinyllama"]
         :pre     "Explain in three points the following sentence:\n %s"
         :prompts ["Why is the sky blue"]}
        identity
        (fn [data]
          (println "====")
          (clojure.pprint/pprint (result-map data))
          (reset! processing false)
          )))
    (while @processing
      (Thread/sleep 1000))))

(deftest llama-and-blue
  (let [results (pgen
                  {
                   :url "http://localhost:11434,http://localhost:11432"
                   :models  ["llama3.1"]
                   :pre     "Explain in %s points the following sentence:\n %s"
                   :prompts [["2" "Why is the sky blue"]
                             ["3" "Why are the smurfs blue"]
                             ["5" "Why is my sweater blue"]
                             ["5" "Why is my sweater blue"]
                             ["5" "Why is my sweater blue"]
                             ["5" "Why is my sweater blue"]]}
                  )]

    (clojure.pprint/pprint
      (map #(-> [(:prompt %) (:result %) (:url %)]) results))))


(deftest blue-sky-test
  (->>
    {:url "http://localhost:11432,http://localhost:11434"
     :models  ["llama3.1"]
     :format  {:type "integer"}
     :pre     "This is a potential answer %s02 for this question: %s01. Give a score to the answer on a scale 1 to 100: based on how accurate it.
       - Do not give an answer yourself.
       - No comment.
       - No explanation.
       - No extra text. "
     :prompts [["Why is the sky blue" "The sky appears blue because of a process called Rayleigh scattering."]
               ["Why is the sky blue" "Blue is scattered more than other colors because it travels as shorter, smaller waves."]
               ["Why is the sky blue" "During the day the sky looks blue because it's the blue light that gets scattered the most. "]
               ["Why is the sky blue" "Because it is Christmas. "]
               ]}
    (pyjama.parallel/pgen)
    (map #(-> [(:prompt %) (:result %) (:url %)]))
    (clojure.pprint/pprint)))