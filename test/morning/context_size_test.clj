(ns morning.context-size-test
  (:require
    [clojure.test :refer :all]
    [pyjama.functions]))

; do not try this on a small GPU
(deftest
  llama31-full-size
  "Do not try this"
  ((pyjama.functions/ollama-fn
     {:model  "llama3.1"
      :options {:num_ctx 131072}
      :stream true}) "tell me a joke"))