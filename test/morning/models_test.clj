(ns morning.models_test
  (:require
    [pyjama.models :refer :all]
    [clojure.test :refer :all]))

(deftest fetch-and-sort

  (let [models (fetch-remote-models)]
    (clojure.pprint/pprint (filter-models models "mistral"))
    ;; Test sorting by :name in ascending order
    (clojure.pprint/pprint (sort-models models :name :asc))

    ;; Test sorting by :pulls in descending order
    (clojure.pprint/pprint (sort-models models :pulls :desc))

    ))