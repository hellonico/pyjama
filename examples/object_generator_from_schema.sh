#!/bin/sh
#_(
  DEPS='
   {
    :deps
    {org.clojure/clojure {:mvn/version "1.11.0"}
    hellonico/pyjama {:git/url "https://github.com/hellonico/pyjama.git"
                      :sha "78ddeb31464ce16556a99592ec0193277f2c3c5a"}}}
   '

exec clj $OPTS -Sdeps "$DEPS" -M "$0" "$@"

)

(ns generator.example
 (:require [pyjama.personalities.core :as p]))

(def generator
  (p/make-personality
    {
    :format {:type "object"
             :properties {:city {:type "string"}}}}
    ))


(println
 (generator))