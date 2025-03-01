(ns morning.openrouter-test
  (:require
    [clojure.java.io :as io]
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
    (prompt-for-model
      "Write Fibonacci in Clojure!"
      "cognitivecomputations/dolphin3.0-mistral-24b:free")))

(defn get-responses [prompt models]
  (doall (map (fn [model] [model (prompt-for-model prompt model)]) models)))

(defn score-response [prompt qa-pairs scoring-model]
  (map (fn [[model answer]]
         (let [score-prompt (str "Evaluate the following answer:\n\n"
                                 "Question: " prompt "\n"
                                 "Answer: " answer "\n\n"
                                 "Provide:\n"
                                 "- A score from 1 to 10\n"
                                 "- A brief explanation for the score\n"
                                 "- Suggestions for improvement")
               response (prompt-for-model score-prompt scoring-model)]
           [model answer response]))
       qa-pairs))

(defn write-to-markdown [file prompt scored-responses]
  (with-open [writer (io/writer file)]
    (binding [*out* writer]
      (println "# AI Model Comparison Report")
      (println)
      (println "## Prompt")
      (println "```clojure")
      (println prompt)
      (println "```")
      (println)

      (doseq [[model answer evaluation] scored-responses]
        (println (str "## Model: `" model "`"))
        (println)
        (println "**Answer:**")
        (println "```clojure")
        (println answer)
        (println "```")
        (println)
        (println "**Evaluation:**")
        (println evaluation)
        (println "---"))

      ;; Find the best model and response
      (let [parsed-scores (map (fn [[model _ evaluation]]
                                 (let [score (re-find #"\d+" evaluation)]
                                   [(Integer. (or score "0")) model evaluation]))
                               scored-responses)
            best (apply max-key first parsed-scores)]

        (println "# Conclusion")
        (println)
        (println "**Best Model:** `" (second best) "`")
        (println)
        (println "**Best Answer:**")
        ;(println "```clojure")
        (println (nth best 2)) ;; Best answer
        ;(println "```")
        (println)
        (println "## Summary")
        (println "- `" (second best) "` provided the highest-rated answer.")
        (println "- Consider using this model for similar tasks in the future.")))))


(deftest compare-models
  (let [prompt "Write Fibonacci in Clojure!"
        models ["nousresearch/deephermes-3-llama-3-8b-preview:free"
                "google/gemini-2.0-flash-lite-001"
                "perplexity/r1-1776"]
        scoring-model "cognitivecomputations/dolphin3.0-r1-mistral-24b:free"
        responses (get-responses prompt models)
        scored-responses (score-response prompt responses scoring-model)]
    (write-to-markdown "results.md" prompt scored-responses)))
