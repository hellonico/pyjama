(ns pyjama.models
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [hickory.core :as hickory]
            [hickory.select :as select]))

(defn fetch-and-parse-html [url]
  (-> url
      client/get
      :body
      hickory/parse
      hickory/as-hickory))

(defn extract-li-elements [parsed-html]
  (select/select
    (select/descendant
      (select/and
        (select/tag :ul)
        (select/attr :role #(= % "list"))
        (select/class "grid")
        (select/class "grid-cols-1")
        (select/class "gap-y-3"))
      (select/tag :li))
    parsed-html))

(defn extract-name [li-element]
  (->> (select/select
         (select/and
           (select/tag :span)
           (select/class "group-hover:underline"))
         li-element)
       first
       :content
       first))

(defn extract-description [li-element]
  (->> (select/select
         (select/tag :p)
         li-element)
       first
       :content
       first))

(defn extract-updated [li-element]
  (->> (select/select
         (select/and
           (select/tag :span)
           (select/attr :x-test-updated identity))          ;; Match any value for x-test-updated
         li-element)
       first
       :content
       first))

(defn extract-capability [li-element]
  (->> (select/select
         (select/and
           (select/tag :span)
           (select/attr :x-test-capability identity))
         li-element)
       first
       :content
       first))

(defn extract-pulls [li-element]
  (->> (select/select
         (select/and
           (select/tag :span)
           (select/attr :x-test-pull-count identity))
         li-element)
       first
       :content
       first))

(defn extract-sizes [li-element]
  (->> (select/select
         (select/and
           (select/tag :span)
           (select/attr :x-test-size identity))
         li-element)
       (map #(-> % :content first))))

(defn scrape-items [url]
  (let [parsed-html (fetch-and-parse-html url)
        li-elements (extract-li-elements parsed-html)]
    (map (fn [li]
           {:name        (extract-name li)
            :description (extract-description li)
            :updated     (extract-updated li)
            :capability  (extract-capability li)
            :pulls       (extract-pulls li)
            :sizes       (extract-sizes li)})
         li-elements)))

(defn fetch-remote-models []
  (scrape-items "https://ollama.com/library?sort=newest"))

(defn sort-models [models sort-key sort-direction]
  (let [getter (fn [model] (get model sort-key))]           ;; Getter to extract the sort value
    (if (= sort-direction :asc)
      (sort-by getter models)                               ;; Use sort-by for ascending order
      (reverse (sort-by getter models)))))                  ;; Use reverse for descending order

(defn filter-models [models query]
  (if (str/blank? (str query))                              ;; Ensure `query` is a string before using `str/blank?`
    models
    (filter (fn [model]
              (cond
                (string? model) (str/includes?
                                  (str/lower-case model)
                                  (str/lower-case query))
                :else
                (some (fn [[_ v]]
                        (let [value-str (cond
                                          (string? v) v
                                          (coll? v) (str/join " " v)
                                          :else (str v))]   ;; Convert everything to string
                          (str/includes? (str/lower-case value-str)
                                         (str/lower-case query)))) ;; Case-insensitive search
                      model)))
            models)))

(defn get-sizes
  "get all size available for a model"
  [models model]
  (:sizes (first (filter #(= (:name %) model) models))))

(defn get-installed-sizes
  "get the installed sizes for a given installed model"
  [models model-name]
  (->>
    models
    (filter #(and (clojure.string/starts-with? % (str model-name ":"))))
    (map #(str/replace % (str model-name ":") ""))))


;
; query local models
;
;
;(defn local-models
;  ([url query]
;   (->> (if (vector? query)
;          (map #(filter-models (local-models url) %) query)
;          (filter-models (local-models url) query ))
;        (flatten)))
;  ([url]
;   (pyjama.core/ollama url :tags {} (fn [res] (map :name (res :models))))))
;
;(defn local-models-strip-latest
;  ([url query]
;   (->> (if (vector? query)
;          (map #(filter-models (local-models-strip-latest url) %) query)
;          (filter-models (local-models-strip-latest url) query ))
;        (flatten))) ; Flatten the results
;  ([url]
;   (pyjama.core/ollama url :tags {} (fn [res] (map #(str/replace (:name %) #":latest" "") (res :models))))))

(defn fetch-models
  [url strip-latest?]
  (pyjama.core/ollama
    url
    :tags {}
    (fn [res]
      (map #(if strip-latest?
              (clojure.string/replace % #":latest" "")
              %)
           (map :name (res :models))))))

(defn local-models-common
  [url query strip-latest?]
  (->> (if (vector? query)
         (map #(filter-models (local-models-common url % strip-latest?) %) query)
         (filter-models (fetch-models url strip-latest?) query))
       (flatten)))

(defn local-models
  ([url query] (local-models-common url query false))
  ([url] (fetch-models url false)))

(defn local-models-strip-latest
  ([url query] (local-models-common url query true))
  ([url] (fetch-models url true)))
