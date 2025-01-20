(ns morning.embeddings-test
  (:require [clojure.core]
            [clojure.test :refer :all]
            [pyjama.core]
            [pyjama.embeddings :refer [enhance-prompt generate-vectorz-documents]]))

(def url "http://localhost:11434")
(def embedding-model "granite-embedding")

(deftest pull-embeddings-model
  (->
    (pyjama.core/ollama url :pull {:model embedding-model})
    (println)))

(deftest some-embeddings
  (let [sentences ["The sky is blue because the smurfs are blue."
                   "The sky is red in the evening because the grand smurf is too."]
        documents (generate-vectorz-documents
                    {:documents sentences :url url :embedding-model embedding-model})
        question "why is the sky blue?"
        base-prompt "Answer the coming question using no previous knowledge and ONLY knowledge from the context."
        enhanced_prompt (enhance-prompt
                          {
                           :base-prompt     base-prompt
                           :question        question
                           :url             url
                           :embedding-model embedding-model
                           :top-n           1
                           :documents       documents
                           })
        _ (println enhanced_prompt)
        ]
    (clojure.pprint/pprint
      (pyjama.core/ollama
        url
        :generate
        {:model "tinyllama" :prompt enhanced_prompt}))))