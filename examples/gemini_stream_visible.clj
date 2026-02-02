(ns examples.gemini-stream-visible
  "Demo that makes streaming actually visible with longer content"
  (:require [pyjama.gemini.core :as gemini]))

(defn -main []
  (println "=== Gemini Streaming - Visible Demo ===\n")
  (println "Asking for a longer story so you can SEE the streaming...\n")
  (println "Watch the text appear token by token:\n")
  (println "---")

  (let [start-time (System/currentTimeMillis)
        chunk-count (atom 0)]

    (gemini/gemini-stream
     {:model "gemini-2.5-flash"
      :prompt "Write a creative 200-word science fiction story about a programmer who discovers their code is alive. Make it dramatic and engaging."}

     ;; on-chunk callback - called for each text chunk
     (fn [chunk]
       (swap! chunk-count inc)
       (print chunk)
       (print "\u001B[36m●\u001B[0m")  ; Cyan dot to mark each chunk
       (flush))

     ;; on-complete callback
     (fn []
       (let [elapsed (- (System/currentTimeMillis) start-time)]
         (println "\n---")
         (println)
         (println "✓ Streaming complete!")
         (println "  Time elapsed:" elapsed "ms")
         (println "  Chunk count:" @chunk-count)
         (println "  Average time per chunk:" (int (/ elapsed @chunk-count)) "ms"))))))

(comment
  (-main))
