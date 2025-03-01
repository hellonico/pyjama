(ns morning.openrouter-test
  (:require
    [pyjama.openrouter.core :as openrouter]
    [clojure.test :refer :all]))

(deftest claude-write-fibonacci
  (let [response (openrouter/with-prompt "Write Fibonacci in Clojure!")]
    (println
      (->
        response
        :choices first :message :content))))

(defn prompt-for-model[prompt model]
  (let [response (openrouter/with-config {:prompt prompt :model model})]
      (-> response :choices first :message :content)))

(deftest mistral-write-fibonacci
  (println
    (prompt-for-model "Write Fibonacci in Clojure!" "cognitivecomputations/dolphin3.0-mistral-24b:free")))

(ns model-comparator
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(defn prompt-for-model [prompt model]
  (let [response (openrouter/with-config {:prompt prompt :model model})]
    (-> response :choices first :message :content)))

(defn get-responses [prompt models]
  (map (fn [model] [model (prompt-for-model prompt model)]) models))

(defn score-response [qa-pairs scoring-model]
  (map (fn [[model answer]]
         (let [score-prompt (str "Score this answer from 1-10, explain the score, and suggest improvements:\n"
                                 "Question: " prompt "\n"
                                 "Answer: " answer)
               response (prompt-for-model score-prompt scoring-model)]
           [model answer response]))
       qa-pairs))

(defn write-to-csv [file data]
  (with-open [writer (io/writer file)]
    (csv/write-csv writer data)))

(defn main []
  (let [prompt "Write Fibonacci in Clojure!"
        models ["nousresearch/deephermes-3-llama-3-8b-preview:free"
                "google/gemini-2.0-flash-lite-001"
                "perplexity/r1-1776"]
        scoring-model "cognitivecomputations/dolphin3.0-r1-mistral-24b:free"
        responses (get-responses prompt models)
        scored-responses (score-response responses scoring-model)
        csv-data (concat [["Model" "Answer" "Score & Explanation"]]
                         scored-responses)]
    (write-to-csv "responses.csv" csv-data)))

;(main)
