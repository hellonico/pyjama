(ns examples.gemini-stream
  (:require [pyjama.gemini.core :as gemini]))

(defn -main []
  (println "=== Gemini Streaming Demo ===\n")

  ;; Example 1: Simple streaming with callback
  (println "1. Simple streaming story:")
  (println "   Prompt: 'Tell me a short story about a robot'\n")
  (gemini/gemini-stream
   {:model "gemini-2.5-flash"
    :prompt "Tell me a very short story about a robot learning to dance. Keep it under 100 words."}
   (fn [chunk]
     (print chunk)
     (flush))
   (fn []
     (println "\n✓ Stream complete!\n")))

  ;; Example 2: Streaming with text accumulation
  (println "\n2. Streaming with accumulation:")
  (println "   Prompt: 'Count from 1 to 10'\n")
  (let [accumulated (atom "")
        result (gemini/gemini-stream
                {:model "gemini-2.0-flash"
                 :prompt "Count from 1 to 10, separating each number with a comma."}
                (fn [chunk]
                  (swap! accumulated str chunk)
                  (print chunk)
                  (flush))
                (fn []
                  (println)))]
    (println "\n   Accumulated text length:" (count @accumulated) "characters")
    (println "   Return value length:" (count result) "characters")
    (println "   ✓ Stream complete!\n"))

  ;; Example 3: Streaming code generation
  (println "\n3. Streaming code generation:")
  (println "   Prompt: 'Write a Python function to reverse a string'\n")
  (gemini/gemini-stream
   {:model "gemini-2.0-flash"
    :prompt "Write a simple Python function to reverse a string. Just the code, no explanation."}
   (fn [chunk]
     (print chunk)
     (flush))
   (fn []
     (println "\n✓ Stream complete!")))

  (println "\n=== Demo Complete ==="))

(comment
  ;; Run from REPL:
  (-main)

  ;; Or from command line:
  ;; clj -M -e "(load-file \"examples/gemini-stream.clj\")(examples.gemini-stream/-main)"
  )
