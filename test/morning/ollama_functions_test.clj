(ns morning.ollama-functions-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [pyjama.validate :refer [validate-text]]
            [pyjama.functions :as pj]))

;;–– Functions under test ––;;

(def simple-city-generator
  (pyjama.functions/ollama-fn
    {:system "generate an existing city name"
     :model  "llama3.2"
     :format {:type "string"}}))

(def city-generator
  (pyjama.functions/ollama-fn
    {:system "generate an object according to schema"
     :model  "llama3.2"
     :format {:type "object"
              :properties {:city {:type "string"}}}}))

(def calculator
  (pyjama.functions/ollama-fn
    {:model  "llama3.2"
     :system "answer the computation, only the numerical value of the result"
     :format {:type "number"}}))

(def airport-code-generator
  (pyjama.functions/ollama-fn
    {:system "generate array of object: the original city and the corresponding 3 letters code for the main airport, with no extra text."
     :model  "llama3.2"
     :format
     {:type                 "array"
      :minItems             2
      :maxItems             2
      :additionalProperties false
      :items
      {:type                 "object"
       :required             ["airport" "city"]
       :additionalProperties false
       :properties           {:city    {:type "string"}
                              :airport {:type "string" :maxLength 3}}}}}))

(def keyworder
  (pyjama.functions/ollama-fn
    {:model  "llama3.2"
     :system "In Japanese, Find all the main keywords in the prompt. relevance is how important it is, between 1 and 10."
     :stream false
     :format {:type     "array"
              :minItems 2
              :maxItems 7
              :items    {:type       "object"
                         :required   [:keyword :relevance]
                         :properties {:keyword   {:type "string"}
                                      :relevance {:type "integer"}}}}}))

(def opening-crawl
  "It is a period of civil wars in the galaxy. A brave alliance of underground freedom fighters has challenged the tyranny and oppression of the awesome GALACTIC EMPIRE.\n\nStriking from a fortress hidden among the billion stars of the galaxy, rebel spaceships have won their first victory in a battle with the powerful Imperial Starfleet. The EMPIRE fears that another defeat could bring a thousand more solar systems into the rebellion, and Imperial control over the galaxy would be lost forever.\n\nTo crush the rebellion once and for all, the EMPIRE is constructing a sinister new battle station. Powerful enough to destroy an entire planet, its completion spells certain doom for the champions of freedom.")

(def scorer-config
  {:model   "llama3.1"
   :options {:temperature 0.9}
   :stream  false
   :pre     "Given the question: '''%s''' and the answer: '''%s''', score (1–50 bad, 51–80 good, 81–100 perfect) based solely on logical relevance."
   :format  {:type       "object"
             :required   [:score :category :explanation]
             :properties {:score       {:type "integer" :minimum 0 :maximum 100}
                          :category    {:type "string"  :enum ["bad" "good" "perfect"]}
                          :explanation {:type "string"}}}})

(def scorer
  (pyjama.functions/ollama-fn scorer-config))

(def synonyms-config
  {:model  "llama3.1"
   :options {:temperature 0.9}
   :stream false
   :pre    "Provide between 3 and 5 synonyms for this word: %s"
   :format {:type  "array"
            :minItems 3
            :maxItems 5
            :items {:type "string"}}})

(def synonyms-generator
  (pyjama.functions/ollama-fn synonyms-config))


;;–– Validation tests using validate-text ––;;

(deftest simple-city-generator-validation-test
  (let [summary
        (validate-text
          simple-city-generator
          [{:in "city is tokyo"           :validators {:min-length 1}}
           {:in "random city in Africa"   :validators {:min-length 1}}])]
    (println "\nSimple City Generator summary:" summary)
    (is (= 0 (:failed summary)))))

(deftest city-generator-validation-test
  (let [summary
        (validate-text
          city-generator
          [{:in "city is tokyo"
            :validators
            {:comparator (fn [out]
                           (and (map? out)
                                (string? (:city out))))}}
           {:in "random city in Africa"
            :validators
            {:comparator (fn [out]
                           (and (map? out)
                                (string? (:city out))))}}])]
    (println "\nCity Generator summary:" summary)
    (is (= 0 (:failed summary)))))

(deftest calculator-validation-test
  (let [summary
        (validate-text
          calculator
          [{:in "3 x 7"
            :validators {:comparator (fn [out] (= out 21))}}])]
    (println "\nCalculator summary:" summary)
    (is (= 0 (:failed summary)))))

(deftest airport-code-generator-validation-test
  (let [summary
        (validate-text
          airport-code-generator
          [{:in "Paris and New York"
            :validators
            {:comparator
             (fn [out]
               (and (seq? out)
                    (= 2 (count out))
                    (every? #(and (string? (:city %))
                                  (string? (:airport %))
                                  (<= (count (:airport %)) 3))
                            out)))}}])]
    (println "\nAirport Code Generator summary:" summary)
    (is (= 0 (:failed summary)))))

(deftest keyworder-validation-test
  (let [summary
        (validate-text
          keyworder
          [{:in opening-crawl
            :validators
            {:comparator
             (fn [out]
               (and (seq? out)
                    (<= 2 (count out) 7)
                    (every? #(and (string? (:keyword %))
                                  (integer? (:relevance %))
                                  (<= 1 (:relevance %) 10))
                            out)))}}])]
    (println "\nKeyworder summary:" summary)
    (is (= 0 (:failed summary)))))

(deftest scorer-validation-test
  (let [summary
        (validate-text
          scorer
          [{:in ["What is 2+2?" "4"]
            :validators
            {:comparator
             (fn [out]
               (and (map? out)
                    (contains? #{"bad" "good" "perfect"} (:category out))
                    (string? (:explanation out))
                    (integer? (:score out))
                    (<= 0 (:score out) 100)))}}])]
    (println "\nScorer summary:" summary)
    (is (= 0 (:failed summary)))))

(deftest synonyms-generator-validation-test
  (let [summary
        (validate-text
          synonyms-generator
          [{:in "slow"
            :validators
            {:comparator
             (fn [out]
               (and (vector? out)
                    (<= 3 (count out) 5)
                    (every? string? out)))}}])]
    (println "\nSynonyms Generator summary:" summary)
    (is (= 0 (:failed summary)))))



;;–– Personality functions converted to ollama-fn ––;;

(def japanese-translator
  (pj/ollama-fn
    {:model  "llama3.2"
     :system "Translate each sentence from Japanese to English with no explanation and no other text than the translation."
     :format {:type "string"}}))

(def samuraizer
  (pj/ollama-fn
    {:model  "llama3.2"
     :system "Summarize each text you are getting, with no explanation and no other text than the summary, using Samurai language."
     :format {:type "string"}}))

(def three-points
  (pj/ollama-fn
    {:model  "llama3.2"
     :system "Summarize the whole text you are getting in a list of three points maximum. YOU CANNOT WRITE MORE THAN THREE SENTENCES IN YOUR WHOLE ANSWER, with no explanation and no other text than the summary."
     :format {:type     "array"
              :minItems 1
              :maxItems 3
              :items    {:type "string"}}}))

(def dad
  (pj/ollama-fn
    {:model  "llama3.2"
     :system "You are a Dad explaining things to your teenager son/daughter. You cannot use difficult words, and you answer with great details and humour the question you are asked."
     :format {:type "string"}}))


;;–– Tests using validate-text ––;;

(deftest japanese-translator-validation-test
  (let [summary
        (validate-text
          japanese-translator
          [{:in         "猫が好きです。"
            :validators {:contains ["I like"]}}])]
    (println "\nJapanese Translator summary:" summary)
    (is (= 0 (:failed summary)))))

(deftest samuraizer-validation-test
  (let [input   "The mountains tremble under the cherry blossoms."
        summary
        (validate-text
          samuraizer
          [{:in         input
            :validators {:min-length 10
                         :max-length 200}}])]
    (println "\nSamuraizer summary:" summary)
    (is (= 0 (:failed summary)))))

(deftest three-points-validation-test
  (let [text    "Clojure is a modern Lisp that runs on the JVM. It emphasizes immutable data structures. It encourages functional programming."
        summary
        (validate-text
          three-points
          [{:in         text
            :validators
            {:comparator
             (fn [out]
               (and (seq? out)
                    (<= (count out) 3)
                    (every? string? out)))}}])]
    (println "\nThree Points summary:" summary)
    (is (= 0 (:failed summary)))))

(deftest dad-validation-test
  (let [question "Explain blockchain technology."
        summary
        (validate-text
          dad
          [{:in         question
            :validators {:min-length 20}}])]
    (println "\nDad summary:" summary)
    (is (= 0 (:failed summary)))))