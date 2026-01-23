(ns pyjama.tools.llm
  "LLM analysis tools for generating and saving content"
  (:require [clojure.java.io :as io]
            [pyjama.core :as pyjama]))

(defn call-llm-and-save
  "Call LLM and save result directly to file"
  [{:keys [prompt output-file]}]
  (let [result (pyjama.core/call* {:prompt prompt})]
    (when output-file
      (spit output-file result))
    {:text result
     :status :ok}))
