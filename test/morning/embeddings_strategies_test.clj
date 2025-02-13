(ns morning.embeddings-strategies-test
  (:require
    [clojure.test :refer :all]
    [pyjama.embeddings]
    [pyjama.io.print]
    [pyjama.utils]))

(def url (or (System/getenv "OLLAMA_URL")
             "http://localhost:11432"))

(def embedding-model
  "granite-embedding")

(def source-of-truth
  (pyjama.utils/load-lines-of-file "test/morning/source_of_truth.txt"))

(def test-config {:url             url
                  :chunk-size      30
                  :documents       source-of-truth
                  :embedding-model embedding-model})

(defn strategy-and-question [config question strategy]
  (let [documents (pyjama.embeddings/generate-vectorz-documents config)

        config (assoc config
                 :question question
                 :documents documents
                 :strategy strategy
                 :top-n 1                                   ; default is 1 already
                 )

        enhanced-context (pyjama.embeddings/enhanced-context config)
        ]
    enhanced-context))

(deftest strategies-test-three
  (println
    "\n"
    (strategy-and-question test-config "Why is the sky red" :manhattan)
    "\n"
    (strategy-and-question test-config "Why did the fireworks explode" :manhattan)
    "\n"
    (strategy-and-question test-config "Why did the sun rise" :manhattan)))


(defn generate-results [strategies questions]
  (flatten
    (map (fn [question]
           (map (fn [strategy]
                  {:strategy strategy
                   :question question
                   :document (strategy-and-question test-config question strategy)}) strategies))
         questions)))

;
; Batch questions, one strategy
;
(def questions
  (pyjama.utils/load-lines-of-file "test/morning/questions.txt"))

; all question, only :euclidean
(deftest strategy-and-question-cosine
  (pyjama.io.print/print-table
    [:strategy :question :document]
    (generate-results [:euclidean] questions)))

;
; Batch questions and all strategies
;
(def strategies [:cosine
                 :euclidean
                 :dot
                 :manhattan
                 :minkowski
                 :jaccard
                 :pearson])

(deftest strategies-and-one-question
  (pyjama.io.print/print-table
    [:strategy :question :document]
    (generate-results strategies ["Why did the sun rise"])))

(deftest strategy-and-question-test
  (pyjama.io.print/print-table
    [:strategy :question :document]
    (generate-results strategies questions)))