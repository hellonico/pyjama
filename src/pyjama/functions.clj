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
 (let [res (io/resource edn-resource-path)
       config (-> res slurp edn/read-string)]
  `(do
    ~@(for [[fn-key fn-config] config]
       `(def ~(symbol (name fn-key))
         (ollama-fn ~fn-config))))))


(defollama-from-edn "functions.edn")

(defn define-generated-fn
 "Call a code-generator fn at runtime to produce a full `defn` form,
  then `read-string` + `eval` it so that the new fn is defined in this namespace.

  • generator-fn: a function (String -> String) that returns code
  • fn-sym:       the symbol you want to define, e.g. 'reverse-string
  • description:  what the fn should do, e.g. \"reverses its input string s\""
 [generator-fn fn-sym description]
 ;; build a prompt that *names* the function and asks for a full defn form at the top
 (let [prompt (format "Generate a Clojure function named %s that %s. Include the full defn form."
                      (name fn-sym) description)
       code-str (generator-fn prompt)
       _ (println "✅ Defining function:" fn-sym "\n" code-str)
       ;; sometimes the model returns a sequence of strings, so coerce to one big string
       code-str (if (string? code-str) code-str (apply str code-str))
       ;; read it as Clojure data
       code-form (read-string code-str)]
  ;; eval it: this defines the function
  (eval code-form)))

(defmacro def-from-gen
 "Runtime-defines a function from a generator.
  Usage: (def-from-gen clojure-code-generator reverse-string \"reverses a string s\")"
 [generator-fn fn-sym description]
 `(define-generated-fn ~generator-fn '~fn-sym ~description))