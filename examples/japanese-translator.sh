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

(require '[pyjama.personalities.core :as p])

(p/japanese-translator
{:prompt (first *command-line-args*) :stream true})

; ./japanese-translator.sh "このビールとっても美味しいです。どこで見つけたですか？私も買ってみたいです"
; This beer is delicious! Where did you find it? I also want to buy it.