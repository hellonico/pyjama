(ns pyjama.embeddings
  (:require
    [clojure.string :as str]
    [mikera.vectorz.core :as vectorz]
    [pyjama.core]))

(defn chunk-text [text chunk-size]
  (map #(apply str %) (partition-all chunk-size text)))

(defn generate-embeddings [{:keys [url embedding-model]} chunks]
  (pyjama.core/ollama
    url
    :embed
    {:model embedding-model
     :input chunks}))

; TODO: chunk strategy to be implemented here
(defn generate-vectorz-documents [{:keys [chunk-size documents] :as config}]
  (let [chunks (if (vector? documents)
                 documents
                 (chunk-text documents (or chunk-size 2048)))
        embeddings (generate-embeddings config chunks)]
    (map-indexed (fn [idx sentence]
                   {:id        (inc idx)
                    :content   sentence
                    :embedding (vectorz/vec (get embeddings idx))})
                 chunks)))

(defn cosine-similarity [v1 v2]
  (let [dot-product (vectorz/dot v1 v2)
        magnitude (* (vectorz/magnitude v1) (vectorz/magnitude v2))]
    (/ dot-product magnitude)))

(defn top-related-documents [documents query-embedding top-n]
  (->> documents
       (map (fn [doc]
              (assoc doc :similarity
                         (cosine-similarity query-embedding (:embedding doc)))))
       (sort-by :similarity >)
       (take top-n)))

(defn enhanced-context [{:keys [question documents url top-n] :as input}]
  (let [input (assoc input :documents [question])
        query-embedding (:embedding (first (generate-vectorz-documents input)))
        top-docs (top-related-documents documents query-embedding top-n)
        context (->>
                  top-docs
                  (map :content)
                  (clojure.string/join "\n"))]
    context))
