#!/bin/sh
#_(
  DEPS='
   {
    :deps
    {org.clojure/clojure {:mvn/version "1.11.0"}
    hellonico/pyjama {:git/url "https://github.com/hellonico/pyjama.git"
                      :sha "658d69e8c9dc9c88c96104cb55644cfb64b0c67d"}}}
   '

exec clj $OPTS -Sdeps "$DEPS" -M "$0" "$@"

)

(require '[pyjama.personalities.core :as p])

(p/japanese-translator
{:input (first *command-line-args*) :realtime true})

; ./japanese-translator.sh "このビールとっても美味しいです。どこで見つけたですか？私も買ってみたいです"
; This beer is delicious! Where did you find it? I also want to buy it.