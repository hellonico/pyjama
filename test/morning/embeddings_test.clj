(ns morning.embeddings-test
  (:require [clojure.pprint]
            [clojure.test :refer :all]
            [pyjama.core]
            [pyjama.embeddings :refer [enhanced-context generate-vectorz-documents]]))

(def url (or (System/getenv "OLLAMA_URL")
             "http://localhost:11432"))
(def embedding-model
  "granite-embedding")

(deftest pull-embeddings-model
  (->
    (pyjama.core/ollama url :pull {:model embedding-model})
    (println)))

(defn rag [config]
  (let [
        documents
        (generate-vectorz-documents
          (select-keys config [:documents :url :chunk-size :embedding-model]))
        ;_ (pprint-to-file "documents.edn" documents)
        ;documents (load-file "documents.edn")
        ;_ (println documents)
        enhanced-context
        (enhanced-context
          (assoc
            (select-keys config
                         [:question :url :embedding-model :top-n])
            :documents documents
            ))]
    ;(clojure.pprint/pprint
      (pyjama.core/ollama
        url
        :generate
        (assoc
          (select-keys config [:options :stream :model :pre])
          :prompt [enhanced-context (:question config)]
          ))
    ;)
    ))

(deftest some-embeddings
  (let [text "The sky is blue because the smurfs are blue.
              The sky is red in the evening because the grand smurf is too."
        pre "Context: \n\n
        %s.
        \n\n
        Answer the question:
        %s
        using no previous knowledge and ONLY knowledge from the context. No comments.
        Make the answer as short as possible."
        question "why is the sky red?"
        ]
    (rag {:pre             pre
          :url             url
          :model           "mistral"
          :stream          true
          :chunk-size      4096
          :top-n           1
          :question        question
          :documents       text
          :embedding-model embedding-model})))