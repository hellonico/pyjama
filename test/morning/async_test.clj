(ns morning.async-test
  (:require [clojure.core.async :as async]
            [pyjama.core]
            [clojure.test :refer [deftest]]))

(def URL (or (System/getenv "OLLAMA_URL") "http://localhost:11434"))
(def model "llama3.2")
(def prompt "Why is the sky blue?")

(deftest generate-streaming-with-channels
  (let [ch (async/chan)
        _fn (partial pyjama.core/pipe-generate-tokens ch)
        result-ch (async/go
                     (pyjama.core/ollama URL :generate {:stream true :model model :prompt prompt} _fn))
        ]
    (async/go
      (let [_ (async/<! result-ch)]
        (async/close! ch)
        (flush)))
      ; on main thread here.
      (loop []
      (when-let [val (async/<!! ch)]
        (print val)
        (flush)
        (recur)))))

(deftest generate-streaming-with-channels-then-close
  (let [ch (async/chan)
        _fn (partial pyjama.core/pipe-generate-tokens ch)
        result-ch (async/go
                    (pyjama.core/ollama URL :generate {:stream true :model model :prompt prompt} _fn))
        ]
    (async/go
      (let [_ (async/<! result-ch)]
        (async/close! ch)
        (flush)))
    (async/thread
      (loop []
      (when-let [val (async/<!! ch)]
        (print val)
        (flush)
        (recur))))
    ; on main thread here.
    (Thread/sleep 1000)
    (async/close! result-ch)))

(deftest pull-streaming-with-channels
  (let [ch (async/chan)
        _fn (partial pyjama.core/pipe-pull-tokens ch)
        result-ch (async/go
                    (pyjama.core/ollama URL :pull {:stream true :model "llama3.2"} _fn))
        ]
    (async/go
      (let [_ (async/<! result-ch)]
        (async/close! ch)
        (flush)))
    ; on main thread here.
    (loop []
      (when-let [val (async/<!! ch)]
        (print val)
        (flush)
        (recur)))))

(deftest chat-streaming-with-channels
  (let [ch (async/chan)
        _fn (partial pyjama.core/pipe-chat-tokens ch)
        result-ch (async/go
                    (pyjama.core/ollama URL :chat {:messages [{:role :user :content "Who is mario?"}] :stream true :model model } _fn))
        ]
    (async/go
      (let [_ (async/<! result-ch)]
        (async/close! ch)
        (flush)))
    ; on main thread here.
    (loop []
      (when-let [val (async/<!! ch)]
        (print val)
        (flush)
        (recur)))
    ))