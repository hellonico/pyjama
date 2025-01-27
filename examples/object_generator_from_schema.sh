#!/bin/sh
#_(
  DEPS='
   {
    :deps
    {org.clojure/clojure {:mvn/version "1.11.0"}
    hellonico/pyjama {:git/url "https://github.com/hellonico/pyjama.git"
                      :sha "afefd1113a5a04a46226c568fd6d8f6b6342ebd3"}}}
   '

exec clj $OPTS -Sdeps "$DEPS" -M "$0" "$@"

)

(ns generator.example
 (:require [pyjama.personalities.core :as p]))

(def generator
  (p/make-personality
    {
      :prompt "Generate 3 random objects"
    :format {:type "object"
             :properties {:city {:type "string"}}}}
    ))


(println
 (generator))