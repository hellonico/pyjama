(ns morning.utils_test
  (:require
    [clojure.test :refer :all]
    [pyjama.core]
    [pyjama.utils]))

(deftest get-templated-prompt
  (->
    {:template "Explain in three points: %s" :prompt "Super Mario"}
    pyjama.core/templated-prompt
    println))

(deftest get-templated-prompt
  (->
    {:pre "Explain '%s' in %s points. " :prompt ["Super Mario" "five"]}
    pyjama.core/templated-prompt
    println))


(deftest get-markdown-table
  (->
    (pyjama.utils/to-markdown
      {:headers [:flight :time]
       :rows    [{:flight "Air France" :time "3h30"}]
       })
    println
    ))