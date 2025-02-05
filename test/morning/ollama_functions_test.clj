(ns morning.ollama-functions-test
  (:require [clojure.test :refer :all]
            [pyjama.functions]))

(def city-generator
  (pyjama.functions/ollama-fn
    {
     :system "generate an object according to schema"
     :model  "llama3.2"
     :format {:type "object" :properties {:city {:type "string"}}}}))

(deftest generate-test
  (println
    (city-generator "city is tokyo"))
  (println
    (city-generator "random city in Africa")))

(def airport-code-generator
  (pyjama.functions/ollama-fn
    {
     :system "generate array of object: the original city and the corresponding 3 letters code for the main airport, with no extra text."
     :model  "llama3.2"
     :format
     {:type                 "array"
      :items
      {:type                 "object"
       :required             ["airport" "city"]
       :properties           {:city {:type "string"} :airport {:type "string"}}
       :additionalProperties false}
      :additionalProperties false
      :minItems             2
      :maxItems             2}}))

(deftest generate-code-test
  (let [res (airport-code-generator "Paris and New York")]
    (println "Departure city: " (first res))
    (println "Arrival city: " (second res))))

(def keyworder
  (pyjama.functions/ollama-fn
    {
     ;:url    "http://localhost:11432"
     :model
     ;"hellonico/japanese-llama-3-8b-instruct-v2.Q8_0.gguf"
     "llama3.2"
     :format
     {
      :type     "array"
      :minItems 2
      :maxItems 7
      :items    {
                 :type       "object"
                 :required   [:keyword :relevance]
                 :properties {:keyword {:type "string"} :relevance {:type "integer"}}}}
     :system
     "In Japanese, Find all the main keywords in the each prompt. relevance is how important it is in the sentence betweem 1 and 10"
     ;:pre    "In Japanese, Find all the main keywords in the following sentence: %s. relevance is how important it is in the sentence betweem 1 and 10"
     :stream true
     }))

(deftest find-keywords
  (keyworder "高松コンストラクショングループの2025年3月期の受注高の計画は前期比何倍か、小数第三位を四捨五入し答えてください。"))

(def calculator
  (pyjama.functions/ollama-fn
    {
     :url "http://localhost:11434"
     ;:model  "qwen2-math:7b"
     :model  "llama3.2"
     :system "answer the computation, only the numerical value of the result"
     :format {:type "number"}
     }))

; works quite a bit
(deftest find-keywords
  (let [res (calculator "3 x 7")]
  (assert (= 21 res) (str "not = to " res))))
