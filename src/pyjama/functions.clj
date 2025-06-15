(ns pyjama.functions
 (:require
  [clojure.edn :as edn]
  [clojure.java.io :as io]
  [pyjama.personalities]))

(def ollama-fn pyjama.personalities/make-personality)

;; Read your EDN at compile-time and spit out a bunch of (def …) forms.
(defmacro defollama-from-edn
  "Reads a Resource EDN map of `{fn-name config}` and
   defines each var as `(pj/ollama-fn config)`."
  [edn-resource-path]
  ;; slurp & parse right at macroexpand time
  (let [res    (io/resource edn-resource-path)
        config (-> res slurp edn/read-string)]
    `(do
       ~@(for [[fn-key fn-config] config]
           `(def ~(symbol (name fn-key))
              (ollama-fn ~fn-config))))))


(defollama-from-edn "functions.edn")


(defmacro def-generated-function
 "Generates a function dynamically based on a description and language-specific code."
 [fn-name description language]
 (let [model-key (keyword (str language "-code-generator"))
       fn-code   `(pj/ollama-fn {:system ~description
                                 :model  "llama3.2"
                                 :format {:type "string"}})
       code      `(def ~fn-name (fn []
                                 (let [code (str (eval ~fn-code))]
                                  (eval (read-string code)))))]
  `(do
    ~code)))
