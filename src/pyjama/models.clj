(ns pyjama.models
  (:require [clj-http.client :as client]
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
           (select/attr :x-test-updated identity)) ;; Match any value for x-test-updated
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

(defn fetch-remote-models[]
  (scrape-items "https://ollama.com/library?sort=newest"))

(defn -main[& args]
  (clojure.pprint/pprint (fetch-remote-models)))