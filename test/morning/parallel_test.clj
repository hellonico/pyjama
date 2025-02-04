(ns morning.parallel-test
  (:require [clojure.pprint]
            [clojure.test :refer :all]
            [pyjama.parallel :refer :all])
  )


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

(defn pgen [url config]
  (let [app-state (atom {:processing true :url url :tasks {}})]
    (parallel-generate
      app-state
      config
      (fn [res]

        )
      (fn [data]
        (swap! app-state assoc :processing false)
        ;(clojure.pprint/pprint (result-map data))
        ))
    (while (:processing @app-state)
      (Thread/sleep 500))
    (:tasks @app-state)
    )
  )

(deftest llama-and-blue
  (let [results (pgen
                  "http://localhost:11431,http://localhost:11432"
                  {
                   :models  ["llama3.1"]
                   :pre     "Explain in %s points the following sentence:\n %s"
                   :prompts [["2" "Why is the sky blue"]
                             ["3" "Why are the smurfs blue"]
                             ["5" "Why is my sweater blue"]
                             ["5" "Why is my sweater blue"]
                             ["5" "Why is my sweater blue"]
                             ["5" "Why is my sweater blue"]]
                   }
                  )]
    (clojure.pprint/pprint results)
    ))