(ns pyjama.embeddings
  (:require
    [mikera.vectorz.core :as vectorz]
    [pyjama.core]))

(defn fetch-embeddings [url model sentences]
  (pyjama.core/ollama
    url
    :embed
    {:model model :input sentences}))

(defn generate-vectorz-documents [{:keys [url documents embedding-model]}]
  (let [embeddings (fetch-embeddings url embedding-model documents)]
    (map-indexed (fn [idx sentence]
                   {:id        (inc idx)
                    :content   sentence
                    :embedding (vectorz/vec (get embeddings idx))})
                 documents)))

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

(defn enhance-prompt [{:keys [base-prompt question documents url top-n] :as input}]
  (let [
        input (assoc input :documents [question])
        query-embedding (:embedding (first (generate-vectorz-documents input)))
        top-docs (top-related-documents documents query-embedding top-n)
        context (->> top-docs
                     (map :content)
                     (clojure.string/join "\n"))]
    (str "Context:\n" context "\n\n" base-prompt "\n\n" question)))