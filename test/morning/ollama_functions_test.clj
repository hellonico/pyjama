(ns morning.ollama-functions-test
  (:require [clojure.test :refer :all]
            [pyjama.functions]))

(def city-generator
  (pyjama.functions/ollama-fn
    {
     :system "generate an object according to schema"
     :model  "llama3.2"
     :format {:type "object" :properties {:city {:type "string"}}}}))

(deftest generate-test
  (println
    (city-generator "city is tokyo"))
  (println
    (city-generator "random city in Africa"))
  )

(def airport-code-generator
  (pyjama.functions/ollama-fn
    {
     :system "generate array of object: the original city and the corresponding 3 letters code for the main airport, with no extra text."
     :model  "llama3.2"
     :format
     {:type                 "array"
      :items
      {:type                 "object"
       :required             ["airport" "city"]
       :properties           {:city {:type "string"} :airport {:type "string"}}
       :additionalProperties false}
      :additionalProperties false
      :minItems             2
      :maxItems             2}}))

(deftest generate-code-test
  (let[res (airport-code-generator "Paris and New York")]
    (println "Departure city: " (first res))
    (println "Arrival city: " (second res))))