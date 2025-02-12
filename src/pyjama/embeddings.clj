(ns pyjama.embeddings
  (:require
    [clojure.pprint]
    [clojure.string]
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
    (if (zero? magnitude) 0 (/ dot-product magnitude))))

; NOT SUITE
;(defn euclidean-distance [v1 v2]
;  (vectorz/distance v1 v2))
;:euclidean #(Math/negateExact (long (euclidean-distance %1 %2))) ;; Sort by smallest distance

(defn euclidean-similarity [v1 v2]
  (/ 1.0 (+ 1.0 (vectorz/distance v1 v2))))

(defn dot-product-similarity [v1 v2]
  (vectorz/dot v1 v2))

(defn manhattan-distance [v1 v2]
  (reduce + (map #(Math/abs ^float (- %1 %2)) v1 v2)))

(defn jaccard-similarity [v1 v2]
  (let [set1 (set v1)
        set2 (set v2)
        intersection (count (clojure.set/intersection set1 set2))
        union (count (clojure.set/union set1 set2))]
    (if (zero? union) 0 (/ intersection union))))

; NOTE: probably super slow because we convert to double-array each time
(defn pearson-correlation [v1 v2]
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


(defn minkowski-distance [v1 v2 p]
  (Math/pow (reduce + (map #(Math/pow (Math/abs (- %1 %2)) p) v1 v2)) (/ 1.0 p)))
(defn similarity-fn [strategy]
  (case strategy
    :cosine cosine-similarity
    :euclidean euclidean-similarity
    :dot dot-product-similarity
    :manhattan #(Math/negateExact (long (manhattan-distance %1 %2)))
    :jaccard jaccard-similarity
    :pearson pearson-correlation
    :minkowski #(minkowski-distance %1 %2 3)                ;; Default p=3
    cosine-similarity))

(defn top-related-documents [{:keys [strategy documents query-embedding top-n]}]
  (let [sim-fn (similarity-fn (or strategy :cosine))]
    (->> documents
         (map (fn [doc]
                (assoc doc :similarity (sim-fn query-embedding (:embedding doc)))))
         (sort-by :similarity >)
         (take top-n))))

(defn enhanced-context [{:keys [question] :as input}]
  (let [_input (assoc input :documents [question])          ; what is this ? - replace this temporary map name
        query-embedding (:embedding (first (generate-vectorz-documents _input))) ; what is this ? - replace this temporary map name

        input (assoc input :query-embedding query-embedding)
        top-docs (top-related-documents input)
        context (->>
                  top-docs
                  (map :content)
                  (clojure.string/join "\n"))]
    context))

; TODO: move here or not
;(defn strategy-and-question [config question]
;  (let [documents (pyjama.embeddings/generate-vectorz-documents config)
;
;        _config (assoc config
;                  :question question
;                  :documents documents)
;
;        enhanced-context (pyjama.embeddings/enhanced-context _config)
;        ]
;    enhanced-context))

(defn rag-with-documents [config documents]
  (let [enhanced-context
        (pyjama.embeddings/enhanced-context
          (assoc
            (select-keys
              config
              [:question :url :embedding-model :top-n])
            :documents documents
            ))
        _ (if (:debug config) (println enhanced-context))
        ]
    (pyjama.core/ollama
      (:url config)
      :generate
      (assoc
        (select-keys config [:options :images :stream :model :pre :system])
        :prompt [enhanced-context (:question config)])
      (or (:callback config) :response))))
(def simple-rag rag-with-documents)