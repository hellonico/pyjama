(ns morning.context-size-test
  (:require
    [clojure.test :refer :all]
    [pyjama.functions]))

(deftest llama31-full-size
  ((pyjama.functions/ollama-fn
     {;:url    "http://localhost:11432"
      :model  "llama3.1"
      :options {:num_ctx 131072}
      :stream true}) "tell me a joke"))