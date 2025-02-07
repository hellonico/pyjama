(ns morning.ollama-functions-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [pyjama.functions]))

; https://json-schema.org/understanding-json-schema/reference/numeric#range

(def simple-city-generator
  (pyjama.functions/ollama-fn
    {
     :system "generate an existing city name"
     :model  "llama3.2"
     :format {:type "string"}}))

(deftest generate-test-0
  (println
    (simple-city-generator "city is tokyo"))
  (println
    (simple-city-generator "random city in Africa")))

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
    (city-generator "random city in Africa")))

(def calculator
  (pyjama.functions/ollama-fn
    {:model  "llama3.2"
     :system "answer the computation, only the numerical value of the result"
     :format {:type "number"}}))

; works quite a bit
(deftest calculator-test
  (let [res (calculator "3 x 7")]
    (println res)
    (println (class res))
    (assert (= 21 res) (str "not = to " res))))


(def airport-code-generator
  (pyjama.functions/ollama-fn
    {:system "generate array of object: the original city and the corresponding 3 letters code for the main airport, with no extra text."
     :model  "llama3.2"
     :format
     {:type                 "array"
      :items
      {:type                 "object"
       :required             ["airport" "city"]
       :properties           {:city {:type "string"} :airport {:type "string" :maxLength 3}}
       :additionalProperties false}
      :additionalProperties false
      :minItems             2
      :maxItems             2}}))

(deftest generate-code-test
  (let [res (airport-code-generator "Paris and New York")]
    (println "Departure city: " (first res))
    (println "Arrival city: " (second res))))

(def keyworder
  (pyjama.functions/ollama-fn
    {
     :model
     "llama3.2"
     :format
     {
      :type     "array"
      :minItems 2
      :maxItems 7
      :items    {
                 :type       "object"
                 :required   [:keyword :relevance]
                 :properties {:keyword {:type "string"} :relevance {:type "integer"}}}}
     :system
     "In Japanese, Find all the main keywords in the each prompt. relevance is how important it is in the sentence betweem 1 and 10"
     ;:pre    "In Japanese, Find all the main keywords in the following sentence: %s. relevance is how important it is in the sentence betweem 1 and 10"
     :stream true
     }))

(def opening-crawl
  "It is a period of civil wars in the galaxy. A brave alliance of underground freedom fighters has challenged the tyranny and oppression of the awesome GALACTIC EMPIRE.\n\nStriking from a fortress hidden among the billion stars of the galaxy, rebel spaceships have won their first victory in a battle with the powerful Imperial Starfleet. The EMPIRE fears that another defeat could bring a thousand more solar systems into the rebellion, and Imperial control over the galaxy would be lost forever.\n\nTo crush the rebellion once and for all, the EMPIRE is constructing a sinister new battle station. Powerful enough to destroy an entire planet, its completion spells certain doom for the champions of freedom.")
(deftest find-keywords
  (keyworder opening-crawl))

(def scorer-config
  {
   :model   "llama3.1"
   :options {:temperature 0.9}
   :format
   {:type       "object"
    :required   [:score :category :explanation]
    :properties {:category    {:type "string" :enum ["perfect" "good" "bad"]}
                 :explanation {:type "string"}
                 :score       {:type "integer" :minimum 0 :maximum 100}}}
   :pre     "Given the question: %s \n, give a score (bad:1-50 good:50-80 perfect:80-100) based solely on the logical relevance of the answer: %s. \n.
   "
   :stream  false})

(deftest blue-smurf
  (let [questions [["How many smurfs are red?" 1]
                   ["How many lady smurfs in the smurfs world?" 1]
                   ["How many smurfs are red?" -10]
                   ["How many smurfs are blue?" "between 100 and 200"]
                   ["What is the color of smurfs?" "blue"]]]
    (doseq [[question answer] questions]
      (let [{:keys [category explanation score]}
            ((pyjama.functions/ollama-fn scorer-config) [question answer])]
        (println (str/join "," [score category explanation]))))))