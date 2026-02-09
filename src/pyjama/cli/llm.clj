(ns pyjama.cli.llm
  "Unified CLI for chatting with any LLM provider (ChatGPT, Claude, DeepSeek, Gemini, OpenRouter, Ollama)"
  (:gen-class)
  (:require
   [pyjama.core]
   [pyjama.chatgpt.core]
   [pyjama.claude.core]
   [pyjama.deepseek.core]
   [pyjama.gemini.core]
   [pyjama.openrouter.core]
   [pyjama.image]
   [secrets.core]
   [clojure.tools.cli :as cli]
   [clojure.string :as str]))

(def cli-options
  [["-l" "--llm PROVIDER" "LLM provider (chatgpt, claude, deepseek, gemini, openrouter, ollama)"
    :default :chatgpt
    :parse-fn keyword]
   ["-m" "--model MODEL" "Model to use (provider-specific)" :default nil]
   ["-p" "--prompt PROMPT" "Question or prompt to send to the model" :default nil]
   ["-s" "--system SYSTEM" "System prompt" :default nil]
   ["-t" "--temperature TEMP" "Temperature (0.0 to 1.0)" :default nil :parse-fn #(Double/parseDouble %)]
   ["-i" "--images IMAGES" "Image file(s) for vision models"
    :id :images
    :parse-fn identity
    :default []
    :assoc-fn (fn [m k v]
                (update m k (fnil conj []) v))]
   ["-u" "--url URL" "Custom API URL (for Ollama or self-hosted)" :default nil]
   ["-h" "--help"]])

(defn parse-cli-options [args]
  (let [{:keys [options _ _ summary]} (cli/parse-opts args cli-options)]
    (if (:help options)
      (do (println summary) (System/exit 0))
      options)))

(defn get-default-model
  "Get default model for each provider"
  [provider]
  (case provider
    :chatgpt "gpt-4o-mini"
    :claude "claude-3-5-sonnet-20241022"
    :deepseek "deepseek-chat"
    :gemini "gemini-2.0-flash-exp"
    :openrouter "anthropic/claude-3.5-sonnet"
    :ollama "llama3.2"
    "gpt-4o-mini"))

(defn build-config
  "Build configuration map for the LLM call"
  [{:keys [llm model prompt system temperature images url]}]
  (let [base-config {:impl llm
                     :prompt prompt
                     :stream true}]
    (cond-> base-config
      model (assoc :model model)
      (nil? model) (assoc :model (get-default-model llm))
      system (assoc :system system)
      temperature (assoc :temperature temperature)
      (seq images) (assoc :image-paths images)
      url (assoc :url url))))

(defn print-welcome
  "Print welcome message with provider info"
  [provider model]
  (println (str "\n🤖 Pyjama LLM Chat - " (name provider)))
  (println (str "📦 Model: " model))
  (println "💬 Type your message and press Enter. Ctrl+C to exit.\n"))

(defn interactive-chat
  "Interactive chat loop for any LLM provider"
  [{:keys [llm] :as options}]
  (let [model (or (:model options) (get-default-model llm))]
    (print-welcome llm model)

    (loop [messages []]
      (print "\n> ")
      (flush)
      (when-let [user-input (read-line)]
        (if (str/blank? user-input)
          (recur messages)
          (let [config (-> options
                           (assoc :prompt user-input)
                           (assoc :messages (conj messages
                                                  {:role :user
                                                   :content user-input}))
                           build-config)
                result (try
                         (println)
                         (let [response (pyjama.core/pyjama-call config)]
                           (println)
                           {:success true
                            :response response})
                         (catch Exception e
                           (println "\n❌ Error:" (.getMessage e))
                           (println "💡 Tip: Check your secrets configuration and API keys")
                           {:success false}))]
            (if (:success result)
              (recur (conj messages
                           {:role :user :content user-input}
                           {:role :assistant :content (or (:response result) "")}))
              (recur messages))))))))

(defn single-prompt
  "Execute a single prompt and exit"
  [options]
  (let [config (build-config options)]
    (try
      (pyjama.core/pyjama-call config)
      (println)
      (catch Exception e
        (println "❌ Error:" (.getMessage e))
        (println "💡 Tip: Check your secrets configuration and API keys")
        (System/exit 1)))))

(defn -main [& args]
  (let [options (parse-cli-options args)]
    (if (:prompt options)
      (single-prompt options)
      (interactive-chat options))))
