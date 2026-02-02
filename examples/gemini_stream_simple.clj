(ns examples.gemini-stream-simple
  (:require [pyjama.gemini.core :as gemini]))

(defn -main []
  (println "=== Gemini Streaming Example ===\n")
  (println "Streaming response (watch for dots = chunks):\n")

  (let [chunk-count (atom 0)]
    (gemini/gemini-stream
     {:model "gemini-2.5-flash"
      :prompt "Write a haiku about programming."}
     ;; on-chunk callback - called for each text chunk
     (fn [chunk]
       (swap! chunk-count inc)
       ;; Print the chunk text
       (print chunk)
       ;; Print a red dot to show a chunk arrived
       (print "\u001B[31m●\u001B[0m")  ; Red dot with ANSI color
       (flush))
     ;; on-complete callback - called when streaming finishes
     (fn []
       (println "\n")
       (println "✓ Streaming complete!")
       (println "  Total chunks received:" @chunk-count)))))

(comment
  ;; Run from REPL:
  (-main)

  ;; Or from command line:
  ;; clj -M -e "(load-file \"examples/gemini_stream_simple.clj\")(examples.gemini-stream-simple/-main)"
  )
