(ns pyjama.cli.ollama
  (:require [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [pyjama.core]
            [pyjama.image])
  (:gen-class)
  (:import [org.apache.commons.codec.binary Base64]))

(def cli-options
  [["-u" "--url URL" "Base URL for API (default: $OLLAMA_HOST or http://localhost:11434)"]
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
   [nil "--pull" "Automatically pull model if not available"]
   [nil "--list" "List all available models on the server"]
   ["-h" "--help"]])

(defn parse-cli-options [args]
  (let [{:keys [options _ _ summary]} (cli/parse-opts args cli-options)]
    (if (:help options)
      (do (println summary) (System/exit 0))
      options)))

(defn determine-mode
  "Determine the mode based on model name and output file"
  [model output chat?]
  (cond
    chat? :chat
    (and output (re-matches #".*\.(png|jpg|jpeg|webp)$" output)) :image-generation
    (and model (re-matches #".*(image|flux|stable|diffusion).*" model)) :image-generation
    :else :text-generation))

(defn get-default-model
  "Get default model based on mode"
  [mode explicit-model]
  (or explicit-model
      (case mode
        :image-generation "x/z-image-turbo"
        :chat "llama3.2"
        :text-generation "llama3.2"
        "llama3.2")))

(defn extra-characters
  "Returns the extra characters between two strings"
  [current previous]
  (subs current (count previous)))

(defn print-tokens
  "Print tokens from streaming response"
  [parsed keys]
  (let [last-response (atom "")]
    (fn [current]
      (doseq [k keys]
        (when-let [current (get current k)]
          (print (extra-characters current @last-response))
          (flush)
          (reset! last-response current))))))

(defn list-models
  "List all available models on the Ollama server"
  [url]
  (try
    (let [response (pyjama.core/ollama url :tags {})]
      (if-let [models (:models response)]
        models
        []))
    (catch Exception e
      (println (str "❌ Failed to list models: " (.getMessage e)))
      [])))

(defn model-exists?
  "Check if a model exists on the server"
  [url model-name]
  (let [models (list-models url)
        model-names (map (comp str :name) models)
        ;; Check for exact match or with :latest suffix
        model-with-latest (if (.contains model-name ":")
                            model-name
                            (str model-name ":latest"))]
    (or (some #(= % model-name) model-names)
        (some #(= % model-with-latest) model-names))))

(defn pull-model
  "Pull a model from Ollama registry with progress indication"
  [url model]
  (println (str "📥 Pulling model: " model "..."))
  (flush)
  (try
    (pyjama.core/ollama
     url
     :pull
     {:model model
      :stream false}  ; Use non-streaming to avoid messy output
     identity)
    (println (str "✅ Model " model " pulled successfully!"))
    true
    (catch Exception e
      (println (str "❌ Failed to pull model: " (.getMessage e)))
      false)))

(defn ensure-model
  "Ensure a model is available, pulling if necessary"
  [url model]
  (if (model-exists? url model)
    (do
      (println (str "✓ Model " model " already available"))
      true)
    (pull-model url model)))

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
  (let [state (atom {:url url :model model :messages [] :stream stream})]
    (println (str "Starting chat with " model " (type 'exit' to quit)"))
    (loop []
      (print "> ")
      (flush)
      (let [input (read-line)]
        (when (and input (not= input "exit"))
          (let [messages (conj (:messages @state) {:role "user" :content input})
                _ (swap! state assoc :messages messages)
                response (atom "")]
            (pyjama.core/ollama
             url
             :chat
             {:model model
              :messages messages
              :stream stream}
             (fn [parsed]
               (when-let [msg (get-in parsed [:message :content])]
                 (print msg)
                 (flush)
                 (swap! response str msg))))
            (println)
            (swap! state update :messages conj {:role "assistant" :content @response})
            (recur)))))))

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
  (if stream
    ;; Streaming mode - print tokens as they arrive
    (do
      (pyjama.core/ollama
       url
       :generate
       {:images images
        :model model
        :stream true
        :prompt prompt}
       pyjama.core/print-generate-tokens)
      (println)) ; Add newline at end
    ;; Non-streaming mode - get full result
    (let [result (pyjama.core/ollama
                  url
                  :generate
                  {:images images
                   :model model
                   :stream false
                   :prompt prompt})]
      (when result
        (if (or output (= format "markdown"))
          (save-to-markdown result output prompt)
          (println result))))))

(defn -main [& args]
  (let [options (parse-cli-options args)
        {:keys [url images model prompt stream chat width height output format pull list]} options
        ;; Resolve URL at runtime: CLI flag > OLLAMA_HOST env var > default
        final-url (or url (System/getenv "OLLAMA_HOST") "http://localhost:11434")]

    ;; Handle --list flag
    (when list
      (println "📋 Available models on" final-url)
      (println (apply str (repeat 52 "=")))
      (let [models (list-models final-url)]
        (if (empty? models)
          (println "No models found")
          (doseq [m models]
            (let [name-str (str (:name m))  ; Convert to string
                  size-str (when-let [size (:size m)]
                             (clojure.core/format " (%.1f GB)" (double (/ size 1e9))))]
              (println (str "  • " name-str size-str)))))
        (System/exit 0)))

    (let [mode (determine-mode model output chat)
          final-model (get-default-model mode model)]

      ;; Auto-pull model if requested
      (when (and pull final-model)
        (ensure-model final-url final-model))

      (case mode
        :image-generation
        (handle-image-generation final-url final-model prompt width height output)

        :chat
        (handle-chat-mode final-url final-model stream)

        :text-generation
        (handle-text-generation final-url final-model prompt stream images output format)))))