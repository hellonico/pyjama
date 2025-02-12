(ns morning.embeddings-test
  (:require [clojure.pprint]
            [clojure.test :refer :all]
            [pyjama.core]
            [pyjama.utils]
            [pyjama.embeddings :refer [enhanced-context generate-vectorz-documents]]))

(def url (or (System/getenv "OLLAMA_URL")
             "http://localhost:11432"))
(def embedding-model
  "granite-embedding")

(deftest pull-embeddings-model
  (->
    (pyjama.core/ollama url :pull {:model embedding-model})
    (println)))

(def source-of-truth
  (pyjama.utils/load-lines-of-file "test/morning/source_of_truth.txt"))

(deftest some-embeddings
  (let [embed-config {:url             url
                      :chunk-size      300
                      :documents       source-of-truth
                      :embedding-model embedding-model}
        documents (pyjama.embeddings/generate-vectorz-documents embed-config)

        question "why is the sky red?"

        pre "Context: \n\n
        %s.
        \n\n
        Answer the question:
        %s
        using no previous knowledge and ONLY knowledge from the context. No comments.
        Make the answer as short as possible."

        config (assoc embed-config
                 :question question
                 :strategy :euclidean
                 :top-n 1

                 :model "llama3.1"
                 :pre pre)

        ]
    (println (pyjama.embeddings/simple-rag config documents))))