(ns pyjama.embeddings
  (:require
   ;; External dependencies
   [clojure.pprint]
   [clojure.string :as str]
   [mikera.vectorz.core :as vectorz]
   
   ;; Internal dependencies
   [pyjama.core]))

;; =============================================================================
;; Text Processing Functions
;; =============================================================================

(defn chunk-text
  "Split text into chunks of specified size"
  [text chunk-size]
  (map #(apply str %) (partition-all chunk-size text)))

;; =============================================================================
;; Embedding Generation Functions
;; =============================================================================

(defn generate-embeddings
  "Generate embeddings for text chunks using Ollama"
  [{:keys [url embedding-model]} chunks]
  (pyjama.core/ollama
   url
   :embed
   {:model embedding-model
    :input chunks}))

(defn generate-vectorz-documents
  "Generate vectorized documents with embeddings"
  [{:keys [chunk-size documents] :as config}]
  (let [chunks (if (vector? documents)
                 documents
                 (chunk-text documents (or chunk-size 2048)))
        embeddings (generate-embeddings config chunks)]
    (map-indexed (fn [idx sentence]
                   {:id        (inc idx)
                    :content   sentence
                    :embedding (vectorz/vec (get embeddings idx))})
                 chunks)))

;; =============================================================================
;; Similarity Functions
;; =============================================================================

(defn cosine-similarity
  "Calculate cosine similarity between two vectors"
  [v1 v2]
  (let [dot-product (vectorz/dot v1 v2)
        magnitude (* (vectorz/magnitude v1) (vectorz/magnitude v2))]
    (if (zero? magnitude) 0 (/ dot-product magnitude))))

(defn euclidean-similarity
  "Calculate Euclidean similarity (1 / (1 + distance))"
  [v1 v2]
  (/ 1.0 (+ 1.0 (vectorz/distance v1 v2))))

(defn dot-product-similarity
  "Calculate dot product similarity"
  [v1 v2]
  (vectorz/dot v1 v2))

(defn manhattan-distance
  "Calculate Manhattan distance between two vectors"
  [v1 v2]
  (reduce + (map #(Math/abs ^float (- %1 %2)) v1 v2)))

(defn jaccard-similarity
  "Calculate Jaccard similarity between two vectors"
  [v1 v2]
  (let [set1 (set v1)
        set2 (set v2)
        intersection (count (clojure.set/intersection set1 set2))
        union (count (clojure.set/union set1 set2))]
    (if (zero? union) 0 (/ intersection union))))

(defn pearson-correlation
  "Calculate Pearson correlation between two vectors"
  [v1 v2]
  (let [arr1 (double-array (seq v1))
        arr2 (double-array (seq v2))]
    (if (or (empty? arr1) (empty? arr2) (not= (count arr1) (count arr2)))
      0
      (let [mean1 (/ (reduce + arr1) (count arr1))
            mean2 (/ (reduce + arr2) (count arr2))
            centered1 (map #(- % mean1) arr1)
            centered2 (map #(- % mean2) arr2)
            num (reduce + (map * centered1 centered2))
            den (* (Math/sqrt (reduce + (map * centered1 centered1)))
                   (Math/sqrt (reduce + (map * centered2 centered2))))]
        (if (zero? den) 0 (/ num den))))))

(defn minkowski-distance
  "Calculate Minkowski distance between two vectors with parameter p"
  [v1 v2 p]
  (Math/pow (reduce + (map #(Math/pow (Math/abs (- %1 %2)) p) v1 v2)) (/ 1.0 p)))

;; =============================================================================
;; Strategy and Document Ranking Functions
;; =============================================================================

(defn similarity-fn
  "Get similarity function based on strategy"
  [strategy]
  (case strategy
    :cosine cosine-similarity
    :euclidean euclidean-similarity
    :dot dot-product-similarity
    :manhattan #(Math/negateExact (long (manhattan-distance %1 %2)))
    :jaccard jaccard-similarity
    :pearson pearson-correlation
    :minkowski #(minkowski-distance %1 %2 3)
    cosine-similarity))

(defn top-related-documents
  "Find top N most similar documents to query embedding"
  [{:keys [strategy documents query-embedding top-n]}]
  (let [sim-fn (similarity-fn (or strategy :cosine))]
    (->> documents
         (map (fn [doc]
                (assoc doc :similarity (sim-fn query-embedding (:embedding doc)))))
         (sort-by :similarity >)
         (take top-n))))

;; =============================================================================
;; Context Enhancement Functions
;; =============================================================================

(defn enhanced-context
  "Generate enhanced context using RAG approach"
  [{:keys [question] :as input}]
  (let [query-input (assoc input :documents [question])
        query-embedding (:embedding (first (generate-vectorz-documents query-input)))
        input-with-embedding (assoc input :query-embedding query-embedding)
        top-docs (top-related-documents input-with-embedding)
        context (->> top-docs
                    (map :content)
                    (str/join "\n"))]
    context))

;; =============================================================================
;; RAG Implementation Functions
;; =============================================================================

(defn rag-with-documents
  "Implement RAG (Retrieval-Augmented Generation) with documents"
  [config documents]
  (let [enhanced-context
        (enhanced-context
         (assoc
          (select-keys config [:question :url :embedding-model :top-n])
          :documents documents))
        _ (when (:debug config) (println enhanced-context))]
    (pyjama.core/ollama
     (:url config)
     :generate
     (assoc
      (select-keys config [:options :images :stream :model :pre :system])
      :prompt [enhanced-context (:question config)])
     (or (:callback config) :response))))

;; =============================================================================
;; Public API
;; =============================================================================

(def simple-rag rag-with-documents)