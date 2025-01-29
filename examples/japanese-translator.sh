#!/bin/sh
#_(
  DEPS='
   {
    :deps
    {org.clojure/clojure {:mvn/version "1.11.0"}
    hellonico/pyjama {
    :git/url "https://github.com/hellonico/pyjama.git"
    :sha "a7cdf0eeeeb33efc60a9b53511c8896132964dc7"
    }}}
   '

exec clj $OPTS -Sdeps "$DEPS" -M "$0" "$@"

)

(require '[pyjama.personalities  :as p])

(p/japanese-translator
{:prompt (or (first *command-line-args*) "このビールとっても美味しいです。どこで見つけたですか？私も買ってみたいです") :stream true})

; ./japanese-translator.sh "このビールとっても美味しいです。どこで見つけたですか？私も買ってみたいです"
; This beer is delicious! Where did you find it? I also want to buy it.