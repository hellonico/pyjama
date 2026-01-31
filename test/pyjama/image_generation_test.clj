(ns pyjama.image-generation-test
  "Test for Ollama image generation with streaming progress"
  (:require [pyjama.core]
            [clojure.core.async :as async]
            [clojure.test :refer [deftest is testing]]))

(defn test-image-generation
  "Test image generation with a specific size"
  [width height]
  (println (format "\nTesting %dx%d image generation..." width height))

  (let [progress-ch (async/chan)
        combined-callback (fn [parsed]
                            ;; Send progress updates to channel
                            (when (or (:completed parsed) (:total parsed))
                              (async/go (async/>! progress-ch parsed)))
                            ;; Return image data when done
                            (when (:done parsed)
                              (:image parsed)))
        result-ch (async/go
                    (pyjama.core/ollama
                     "http://localhost:11434"
                     :generate-image
                     {:model "x/z-image-turbo"
                      :prompt (format "simple test image %dx%d" width height)
                      :width width
                      :height height
                      :stream true}
                     combined-callback))]

    ;; Monitor progress
    (future
      (loop []
        (when-let [progress-update (async/<!! progress-ch)]
          (when (:completed progress-update)
            (print (format "\rProgress: %d/%d"
                           (:completed progress-update)
                           (:total progress-update)))
            (flush))
          (recur))))

    ;; Wait for result
    (let [image-data (async/<!! result-ch)]
      (async/close! progress-ch)
      (println)

      (is (string? image-data) "Image data should be a string")
      (is (pos? (count image-data)) "Image data should not be empty")

      (when (string? image-data)
        (println (format "✅ Generated %dx%d image: %d bytes" width height (count image-data))))

      image-data)))

(deftest test-64x64-image
  (testing "64x64 image generation"
    (test-image-generation 64 64)))

(deftest test-128x128-image
  (testing "128x128 image generation"
    (test-image-generation 128 128)))

(deftest test-256x256-image
  (testing "256x256 image generation"
    (test-image-generation 256 256)))

(comment
  ;; Run individual tests from REPL
  (test-image-generation 64 64)
  (test-image-generation 128 128)
  (test-image-generation 256 256))
