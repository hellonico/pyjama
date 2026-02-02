(ns examples.gemini-via-call
  "Demo showing Gemini streaming via the main pyjama.core/call function"
  (:require [pyjama.core :as pyjama]))

(defn -main []
  (println "=== Gemini Streaming via pyjama/call ===\n")

  ;; Example 1: Non-streaming (default)
  (println "1. Non-streaming mode:")
  (let [result (pyjama/call {:impl :gemini
                             :model "gemini-2.5-flash"
                             :prompt "Say 'Hello from Gemini' in one sentence."})]
    (println "   Result:" result))
  (println)

  ;; Example 2: Streaming with :stream flag
  (println "2. Streaming mode with :stream flag:")
  (println "   Watch for dots●...\n")
  (let [chunk-count (atom 0)]
    (pyjama/call {:impl :gemini
                  :model "gemini-2.5-flash"
                  :prompt "Write a short haiku about code."
                  :stream true
                  :on-chunk (fn [chunk]
                              (swap! chunk-count inc)
                              (print chunk)
                              (print "\u001B[32m●\u001B[0m")  ; Green dot
                              (flush))
                  :on-complete (fn []
                                 (println)
                                 (println "   ✓ Streamed in" @chunk-count "chunks"))})
    (println))

  ;; Example 3: Streaming with custom callbacks
  (println "3. Streaming with longer content:")
  (println "   (Each cyan dot = one chunk)\n")
  (pyjama/call {:impl :gemini
                :model "gemini-2.0-flash"
                :prompt "In exactly 50 words, describe the beauty of recursive algorithms."
                :streaming true  ; :streaming also works
                :on-chunk (fn [chunk]
                            (print chunk)
                            (print "\u001B[36m●\u001B[0m")  ; Cyan dot
                            (flush))
                :on-complete (fn []
                               (println "\n   ✓ Complete!"))})

  (println "\n=== All tests complete ==="))

(comment
  (-main))
