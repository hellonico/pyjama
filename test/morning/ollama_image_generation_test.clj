(ns morning.ollama-image-generation-test
  (:require [clojure.test :refer [deftest is testing]]
            [pyjama.core :as pj]
            [pyjama.state]
            [clojure.pprint]
            [clojure.java.io :as io])
  (:import [java.util Base64]))

(deftest image-generation-test
  (testing "Generate image using Ollama image generation API"
    (let [result (pj/ollama
                  (or (System/getenv "OLLAMA_URL") "http://localhost:11434")
                  :generate-image
                  {:model  "x/z-image-turbo"
                   :prompt "summer beach a la matisse"
                   :width  512
                   :height 512
                   :stream false})]

      ;; Check that we got a result
      (is (not (nil? result)) "Should receive a result")

      ;; Check that result contains base64-encoded image data
      (when (and result (string? result))
        (is (> (count result) 100) "Image data should be non-empty")

        ;; Try to decode the base64 to verify it's valid
        (try
          (let [decoder (Base64/getDecoder)
                byte-array (.decode decoder result)]
            (is (pos? (count byte-array)) "Decoded image should have data")

            ;; Save the image to file for inspection
            (with-open [out (io/output-stream "/tmp/summer_beach_matisse.png")]
              (.write out byte-array))
            (println "✅ Image generated and saved to /tmp/summer_beach_matisse.png"))
          (catch Exception e
            (is false (str "Failed to decode base64 image: " (.getMessage e)))))))))

(deftest image-generation-streaming-test
  (testing "Generate image with streaming enabled (progress updates)"
    (let [result (pj/ollama
                  (or (System/getenv "OLLAMA_URL") "http://localhost:11434")
                  :generate-image
                  {:model  "x/z-image-turbo"
                   :prompt "summer beach a la matisse"
                   :width  512  ; Smaller for faster testing
                   :height 512
                   :stream true})]

      ;; In streaming mode, the handler will print progress
      ;; The final result should still contain the image
      (is (not (nil? result)) "Should receive a result even in streaming mode"))))

(deftest image-generation-with-state-test
  (testing "Generate image with state progress tracking"
    (let [state (atom {:url (or (System/getenv "OLLAMA_URL") "http://localhost:11434")
                       :model "x/z-image-turbo"})
          _ (pyjama.state/generate-image-stream state "summer beach a la matisse" 512 512)]

      ;; Wait for generation to complete and print progress
      (while (:generating @state)
        (when-let [progress (get-in @state [:generate-image :progress])]
          (println (str "Progress: " (:completed progress) "/" (:total progress))))
        (Thread/sleep 1000))

      ;; Check final state
      (is (not (:generating @state)) "Should not be generating anymore")
      (is (get-in @state [:generate-image :done]) "Should be marked as done")
      (is (get-in @state [:generate-image :image]) "Should have image data")

      ;; Validate the image data
      (when-let [image-data (get-in @state [:generate-image :image])]
        (is (string? image-data) "Image data should be a string")
        (is (> (count image-data) 100) "Image data should be non-empty")

        ;; Try to decode and save
        (try
          (let [decoder (Base64/getDecoder)
                byte-array (.decode decoder image-data)]
            (is (pos? (count byte-array)) "Decoded image should have data")
            (with-open [out (io/output-stream "/tmp/summer_beach_matisse_state.png")]
              (.write out byte-array))
            (println "✅ Image with state tracking saved to /tmp/summer_beach_matisse_state.png"))
          (catch Exception e
            (is false (str "Failed to decode image: " (.getMessage e))))))

      ;; Print final state for inspection
      (println "Final state:")
      (clojure.pprint/pprint @state))))

