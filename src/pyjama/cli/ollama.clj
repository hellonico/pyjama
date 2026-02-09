(ns pyjama.cli.ollama
  (:gen-class)
  (:require
   [pyjama.core]
   [pyjama.image]
   [pyjama.state]
   [clojure.tools.cli :as cli]
   [clojure.string :as str]
   [clojure.java.io :as io])
  (:import [org.apache.commons.codec.binary Base64]))

(def cli-options
  [["-u" "--url URL" "Base URL for API (e.g. http://localhost:11434)" :default "http://localhost:11434"]
   ["-m" "--model MODEL" "Model to use (e.g. llama3.2 for chat, x/z-image-turbo for images)" :default nil]
   ["-s" "--stream STREAM" "Streaming or not" :default true :parse-fn read-string]
   ["-c" "--chat MODE" "Chat mode or not" :default false :parse-fn read-string]
   ["-i" "--images IMAGES" "Image file(s)"
    :id :images
    :parse-fn identity
    :default []
    :assoc-fn (fn [m k v]
                (update m k (fnil conj []) (pyjama.image/image-to-base64 v)))]
   ["-p" "--prompt PROMPT" "Question or prompt to send to the model" :default "Why is the sky blue?"]
   ["-w" "--width WIDTH" "Image width (for image generation models)" :default 1024 :parse-fn #(Integer/parseInt %)]
   ["-g" "--height HEIGHT" "Image height (for image generation models)" :default 768 :parse-fn #(Integer/parseInt %)]
   ["-o" "--output OUTPUT" "Output file (image.png for images, response.md for text)" :default nil]
   ["-f" "--format FORMAT" "Output format: 'markdown' or 'text' (for non-chat mode)" :default "text"]
   ["-h" "--help"]])

(defn parse-cli-options [args]
  (let [{:keys [options _ _ summary]} (cli/parse-opts args cli-options)]
    (if (:help options)
      (do (println summary) (System/exit 0))
      options)))

(defn- extra-characters [res1 res2]
  (let [common-length (count (take-while identity (map #(= %1 %2) res1 res2)))]
    (subs res1 common-length)))

(defn image-generation-model?
  "Check if the model is an image generation model"
  [model]
  (or (str/includes? (str/lower-case model) "image")
      (str/includes? (str/lower-case model) "flux")
      (str/includes? (str/lower-case model) "stable")
      (str/includes? (str/lower-case model) "sdxl")
      (str/includes? (str/lower-case model) "z-image")))

(defn image-output-file?
  "Check if the output file is an image file"
  [output]
  (and output
       (or (str/ends-with? (str/lower-case output) ".png")
           (str/ends-with? (str/lower-case output) ".jpg")
           (str/ends-with? (str/lower-case output) ".jpeg")
           (str/ends-with? (str/lower-case output) ".webp"))))

(defn determine-mode
  "Determine the operation mode based on options"
  [model output chat]
  (cond
    chat :chat
    (image-output-file? output) :image-generation
    (and model (image-generation-model? model)) :image-generation
    :else :text-generation))

(defn get-default-model
  "Get default model based on mode"
  [mode model]
  (if model
    model
    (case mode
      :image-generation "x/z-image-turbo"
      :chat "llama3.2"
      :text-generation "llama3.2"
      "llama3.2")))

(defn animate-spinner
  "Show animated spinner while processing"
  [stop-atom]
  (let [frames ["⠋" "⠙" "⠹" "⠸" "⠼" "⠴" "⠦" "⠧" "⠇" "⠏"]
        start-time (System/currentTimeMillis)
        spinner-thread (Thread.
                        (fn []
                          (loop [i 0]
                            (when-not @stop-atom
                              (let [elapsed (/ (- (System/currentTimeMillis) start-time) 1000.0)
                                    frame (nth frames (mod i (count frames)))]
                                (print (str "\r" frame " Generating... " (format "%.1fs" elapsed)))
                                (flush)
                                (Thread/sleep 100)
                                (recur (inc i)))))))]
    (.setDaemon spinner-thread true)
    (.start spinner-thread)
    spinner-thread))

(defn handle-image-generation
  "Handle image generation for image models"
  [url model prompt width height output]
  (println (str "\n🎨 Generating image with " model "..."))
  (println (str "📐 Size: " width "x" height))
  (println (str "💬 Prompt: " prompt "\n"))

  (let [stop-spinner (atom false)
        start-time (System/currentTimeMillis)
        spinner-thread (animate-spinner stop-spinner)]

    (try
      (let [result (pyjama.core/ollama
                    url
                    :generate-image
                    {:model model
                     :prompt prompt
                     :width width
                     :height height
                     :stream false})
            output-file (or output (str "generated_" (System/currentTimeMillis) ".png"))
            elapsed-time (/ (- (System/currentTimeMillis) start-time) 1000.0)]

        (reset! stop-spinner true)
        (.join ^Thread spinner-thread 500) ; Wait for spinner to finish (max 500ms)
        (print "\r                                        \r") ; Clear spinner line
        (flush)

        (when result
          ;; result is base64 encoded image data, need to decode and save
          (let [image-bytes (if (string? result)
                              (Base64/decodeBase64 ^String result)
                              result)]
            (with-open [^java.io.OutputStream out (io/output-stream output-file)]
              (.write out ^bytes image-bytes)))
          (println (str "✅ Image generated successfully!"))
          (println (str "💾 Saved to: " output-file))
          (println (str "⏱️  Generation time: " (format "%.1f" elapsed-time) "s"))
          output-file))

      (catch Exception e
        (reset! stop-spinner true)
        (.join ^Thread spinner-thread 500) ; Wait for spinner to finish
        (print "\r                                        \r")
        (flush)
        (println (str "❌ Error: " (.getMessage e)))
        (println "💡 Tip: Make sure the model is available on your Ollama server")
        (throw e)))))

(defn handle-chat-mode
  "Handle interactive chat mode"
  [url model stream]
  (let [state (atom {:url url :model model :messages [] :stream stream})
        last-response (atom "")]
    (while true
      (print "\n> ") (flush)
      (reset! last-response "")
      (swap! state update :messages conj {:role :user :content (read-line)})

      (pyjama.state/handle-chat state)

      (while (:processing @state)
        (let [current (:response @state)]
          (print (extra-characters current @last-response))
          (flush)
          (reset! last-response current))))))

(defn save-to-markdown
  "Save response to markdown file"
  [content output prompt]
  (let [filename (or output (str "response_" (System/currentTimeMillis) ".md"))
        markdown-content (str "# Response\n\n"
                              "**Prompt:** " prompt "\n\n"
                              "---\n\n"
                              content "\n")]
    (spit filename markdown-content)
    (println (str "\n💾 Saved to: " filename))
    filename))

(defn handle-text-generation
  "Handle single text generation (non-chat)"
  [url model prompt stream images output format]
  (let [result (pyjama.core/ollama
                url
                :generate
                {:images images
                 :model model
                 :stream false  ; Disable streaming for file output
                 :prompt prompt})]

    (when result
      (if (or output (= format "markdown"))
        (save-to-markdown result output prompt)
        (println result)))))

(defn -main [& args]
  (let [options (parse-cli-options args)
        {:keys [url images model prompt stream chat width height output format]} options
        mode (determine-mode model output chat)
        final-model (get-default-model mode model)]

    (case mode
      :image-generation
      (handle-image-generation url final-model prompt width height output)

      :chat
      (handle-chat-mode url final-model stream)

      :text-generation
      (handle-text-generation url final-model prompt stream images output format))))