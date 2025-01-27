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

(require '[pyjama.personalities.core])

(pyjama.core/ollama
  (or (System/getenv "OLLAMA_URL") "http://localhost:11434")
  :generate
  {:stream true :prompt "What color is the sky at night and why?"})