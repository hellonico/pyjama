(ns pyjama.tools.image
  "Tools for image generation using Ollama"
  (:require [pyjama.core :as pj]
            [clojure.java.io :as io])
  (:import [java.util Base64]))

;; =============================================================================
;; Option 1: Simple Tool Function for Agents
;; =============================================================================

(defn generate-image
  "Generate an image using Ollama and optionally save it to a file.
  
  Parameters:
  - :prompt (required) - Text description of the image to generate
  - :width (optional) - Image width in pixels (default: 512)
  - :height (optional) - Image height in pixels (default: 512)
  - :output-path (optional) - Path to save the generated PNG image
  - :model (optional) - Model to use (default: x/z-image-turbo)
  - :url (optional) - Ollama server URL (default: from OLLAMA_URL env or localhost)
  
  Returns a map with:
  - :image-data - Base64-encoded image data
  - :output-path - Path where image was saved (if provided)
  - :size - Size of decoded image in bytes
  - :status - \"success\" or \"error\"
  - :error - Error message (if failed)
  
  Usage in agent EDN:
  {:id :my-image-agent
   :steps {:generate {:tool pyjama.tools.image/generate-image
                      :args {:prompt \"{{user-prompt}}\"
                             :output-path \"/tmp/generated.png\"}}}}
  "
  [{:keys [prompt width height output-path model url]
    :or {width 512
         height 512
         model "x/z-image-turbo"
         url (or (System/getenv "OLLAMA_URL") "http://localhost:11434")}}]
  (try
    (let [result (pj/ollama url :generate-image
                            {:model model
                             :prompt prompt
                             :width width
                             :height height
                             :stream false})
          decoder (Base64/getDecoder)
          image-bytes (.decode decoder result)]
      (when output-path
        (io/make-parents output-path)
        (with-open [out (io/output-stream output-path)]
          (.write out image-bytes)))
      {:image-data result
       :output-path output-path
       :size (count image-bytes)
       :width width
       :height height
       :model model
       :prompt prompt
       :status "success"})
    (catch Exception e
      {:status "error"
       :error (.getMessage e)
       :prompt prompt})))

;; =============================================================================
;; Option 3: Pipeline Function
;; =============================================================================

(defn generate-and-process
  "Generate image and optionally pass to a processor function.
  
  This is designed for use in pipelines where the image data needs to be
  processed or transformed before being saved or returned.
  
  Parameters:
  - prompt (required) - Text description of the image
  - Options (keyword args):
    - :processor - Function to process the image data (receives base64 string)
    - :width, :height, :model, :url - Same as generate-image
    - :save-decoded - If true, saves decoded bytes instead of base64
  
  Returns:
  If processor is provided: result of (processor image-data)
  Otherwise: same as generate-image
  
  Usage:
  ;; Simple generation
  (generate-and-process \"sunset over mountains\")
  
  ;; With custom processor
  (generate-and-process \"sunset\" 
    :processor (fn [base64-data] 
                 (decode-and-analyze base64-data)))
  
  ;; In a pipeline
  (->> \"beautiful landscape\"
       (generate-and-process :width 1024 :height 768)
       :image-data
       process-further)
  "
  [prompt & {:keys [processor width height model url output-path]
             :or {width 512 height 512}}]
  (let [result (generate-image (merge {:prompt prompt
                                       :width width
                                       :height height}
                                      (when model {:model model})
                                      (when url {:url url})
                                      (when output-path {:output-path output-path})))]
    (if (and processor (= "success" (:status result)))
      (processor (:image-data result))
      result)))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn decode-image
  "Decode base64 image data to bytes.
  
  Usage:
  (decode-image base64-string)
  => byte-array"
  [base64-data]
  (-> (Base64/getDecoder)
      (.decode base64-data)))

(defn save-image
  "Save base64-encoded image data to a file.
  
  Usage:
  (save-image base64-data \"/tmp/output.png\")
  => {:path \"/tmp/output.png\" :size 123456}"
  [base64-data output-path]
  (let [image-bytes (decode-image base64-data)]
    (io/make-parents output-path)
    (with-open [out (io/output-stream output-path)]
      (.write out image-bytes))
    {:path output-path
     :size (count image-bytes)}))

;; =============================================================================
;; Batch Generation
;; =============================================================================

(defn generate-batch
  "Generate multiple images from a list of prompts.
  
  Parameters:
  - prompts - Collection of prompt strings
  - Options (keyword args):
    - :output-dir - Directory to save images (default: /tmp)
    - :prefix - Filename prefix (default: \"image\")
    - :parallel? - Generate in parallel (default: false)
    - Other options passed to generate-image
  
  Returns:
  Vector of results, one per prompt
  
  Usage:
  (generate-batch [\"sunset\" \"ocean\" \"mountains\"]
    :output-dir \"/tmp/images\"
    :width 512
    :height 512)
  "
  [prompts & {:keys [output-dir prefix parallel?]
              :or {output-dir "/tmp" prefix "image" parallel? false}
              :as opts}]
  (let [gen-fn (fn [idx prompt]
                 (let [filename (str prefix "-" (inc idx) ".png")
                       output-path (str output-dir "/" filename)
                       result (generate-image (merge (dissoc opts :output-dir :prefix :parallel?)
                                                     {:prompt prompt
                                                      :output-path output-path}))]
                   (assoc result :index idx :filename filename)))]
    (if parallel?
      (vec (map gen-fn (range) prompts))
      (vec (map-indexed gen-fn prompts)))))
