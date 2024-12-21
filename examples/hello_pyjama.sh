#!/bin/sh
#_(
  DEPS='
   {
    :deps
    {org.clojure/clojure {:mvn/version "1.11.0"}
    hellonico/pyjama {:git/url "https://github.com/hellonico/pyjama.git"
                      :sha "9b0647cf72462bcd690ec28a01a97486a1e886ec"}}}
   '

exec clj $OPTS -Sdeps "$DEPS" -M "$0" "$@"

)

(require '[pyjama.core])
(def url "http://localhost:11434")

; simple generate
(pyjama.core/ollama
  url
  :generate
  {:stream true :prompt "What color is the sky at night and why?"})