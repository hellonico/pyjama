(ns pyjama.runner
  "Interactive agent runner with FZF-based selection and configuration.
   Provides smart analysis workflows with dynamic agent discovery and execution."
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [cheshire.core :as json]
   [pyjama.core :as pyjama]
   [pyjama.agent.core :as agent]
   [pyjama.cli.registry :as registry]))

;; =============================================================================
;; ANSI Color Utilities
;; =============================================================================

(def ^:private colors
  "ANSI color codes for terminal output"
  {:red     "\033[0;31m"
   :green   "\033[0;32m"
   :yellow  "\033[1;33m"
   :blue    "\033[0;34m"
   :purple  "\033[0;35m"
   :cyan    "\033[0;36m"
   :white   "\033[1;37m"
   :gray    "\033[0;90m"
   :reset   "\033[0m"})

(defn- colorize
  "Apply color to text"
  [color text]
  (str (get colors color "") text (get colors :reset "")))

;; =============================================================================
;; Utilities
;; =============================================================================

(defn- check-dependencies
  "Check if required tools (fzf, jq) are installed"
  []
  (let [missing (filterv
                 (fn [tool]
                   (try
                     (let [result (shell/sh "which" tool)]
                       (not (zero? (:exit result))))
                     (catch Exception _ true)))
                 ["fzf" "jq"])]
    (when (seq missing)
      (println (colorize :red (str "❌ Error: Missing required tools: " (str/join ", " missing))))
      (println "   Please install them (e.g., brew install fzf jq)")
      (System/exit 1))))

(defn- print-banner
  "Print the smart analyzer banner"
  []
  (println "╔════════════════════════════════════════════════════════════════╗")
  (println "║                                                                ║")
  (println "║        🧠 PYJAMA SMART ANALYZER 🧠                             ║")
  (println "║                                                                ║")
  (println "║  Dynamic Agent Registry & Execution System                     ║")
  (println "║                                                                ║")
  (println "╚════════════════════════════════════════════════════════════════╝")
  (println))

;; =============================================================================
;; Agent Discovery
;; =============================================================================

(defn- get-all-agents
  "Get all available agents from both local registry and file registry"
  []
  (let [;; Get local agents
        local-agents (pyjama/list-agents)

        ;; Get registry agents
        registry-agents (try
                          (registry/list-registered-agents)
                          (catch Exception _ []))

        ;; Convert registry agents to the same format as local agents
        registry-agents-formatted (map (fn [agent]
                                         {:id (:id agent)
                                          :description (str (:description agent) " [registry]")
                                          :source :registry})
                                       registry-agents)

        ;; Mark local agents with source
        local-agents-marked (map #(assoc % :source :local) local-agents)]

    ;; Combine both lists
    (concat local-agents-marked registry-agents-formatted)))

(defn- format-agents-for-fzf
  "Format agents for FZF selection"
  [agents]
  (->> agents
       (map (fn [agent]
              (str (name (:id agent)) " | " (:description agent "No description provided"))))
       (str/join "\n")))

(defn- run-fzf-agent-select
  "Run FZF to select an agent"
  [agents-text preview-dir]
  (try
    (let [;; Use pre-generated preview files for instant display
          preview-script (str "agent_id=$(echo {} | cut -d'|' -f1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//');"
                              "cat \"" preview-dir "/${agent_id}.txt\" 2>/dev/null || "
                              "echo 'Loading preview...'")
          result (shell/sh "fzf"
                           "--header" "Select an agent to run"
                           "--layout" "reverse"
                           "--border"
                           "--prompt" "🤖 Agent > "
                           "--preview" preview-script
                           "--preview-window" "right:50%"
                           :in agents-text)]
      (when (zero? (:exit result))
        (str/trim (:out result))))
    (catch Exception e
      (println (colorize :red (str "Error running fzf: " (.getMessage e))))
      nil)))

(defn- parse-agent-selection
  "Parse FZF selection to extract agent ID and description"
  [selection]
  (when selection
    (let [parts (str/split selection #"\|")]
      {:id (str/trim (first parts))
       :description (str/trim (or (second parts) ""))})))

(defn- generate-agent-preview
  "Generate preview text for an agent by calling visualize directly"
  [agent-id agents]
  (try
    (let [agent-key (keyword agent-id)
          ;; Find the agent in our list
          selected-agent (first (filter #(= (:id %) agent-key) agents))

          ;; Get the spec - either from local or registry
          spec (if (= (:source selected-agent) :registry)
                 (try
                   (let [{:keys [spec]} (registry/lookup-agent agent-id)]
                     spec)
                   (catch Exception _ nil))
                 (try
                   (let [meta (pyjama/describe-agent agent-key)]
                     (:spec meta))
                   (catch Exception _ nil)))]

      (if spec
        (with-out-str
          (agent/visualize agent-id spec))
        (str "Agent: " agent-id "\n(Preview not available)")))
    (catch Exception e
      (str "Error generating preview: " (.getMessage e)))))

(defn- write-preview-files
  "Write preview files for all agents to temp directory"
  [agents]
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir") "pyjama-previews")]
    (.mkdirs temp-dir)
    (doseq [agent agents]
      (let [agent-id (name (:id agent))
            preview-file (io/file temp-dir (str agent-id ".txt"))
            preview-text (generate-agent-preview agent-id agents)]
        (spit preview-file preview-text)))
    (.getAbsolutePath temp-dir)))

;; =============================================================================
;; Template Handling
;; =============================================================================

(defn- list-templates
  "List available analysis templates from both user and project directories"
  [project-dir]
  (let [user-templates-dir (io/file (System/getProperty "user.home") ".codebase-analyzer" "templates")
        project-templates-dir (io/file project-dir "resources" "templates")
        ;; Backwards compatibility - check old path
        legacy-templates-dir (io/file project-dir "resources" "analysis-templates")

        list-from-dir (fn [dir source-label]
                        (when (.exists ^java.io.File dir)
                          (->> (.listFiles ^java.io.File dir)
                               (filter #(and (.isFile ^java.io.File %)
                                             (str/ends-with? (.getName ^java.io.File %) ".md")
                                             (not (str/starts-with? (.getName ^java.io.File %) "."))))
                               (map (fn [f]
                                      (let [filename (.getName ^java.io.File f)
                                            name (subs filename 0 (- (count filename) 3))
                                            display-name (-> name
                                                             (str/replace "_" " ")
                                                             (str/split #" ")
                                                             (->> (map str/capitalize)
                                                                  (str/join " ")))
                                            display-with-source (str display-name " (" source-label ")")]
                                        {:filename filename
                                         :name name
                                         :display display-with-source
                                         :path (.getAbsolutePath f)
                                         :source source-label}))))))

        user-templates (list-from-dir user-templates-dir "user")
        project-templates (list-from-dir project-templates-dir "project")
        legacy-templates (list-from-dir legacy-templates-dir "project")]

    (->> (concat user-templates project-templates legacy-templates)
         (sort-by :display))))

(defn- format-templates-for-fzf
  "Format templates for FZF selection"
  [templates]
  (->> templates
       (map (fn [t]
              (str (:filename t) " | " (:display t) " | " (:path t))))
       (str/join "\n")))

(defn- run-fzf-template-select
  "Run FZF to select a template"
  [templates-text]
  (try
    ;; Write to temp file and use bash with /dev/tty
    (let [temp-file (java.io.File/createTempFile "fzf-templates-" ".txt")
          _ (spit temp-file templates-text)
          temp-path (.getAbsolutePath temp-file)
          result (shell/sh "bash" "-c"
                           (str "cat " temp-path " | "
                                "fzf --header 'Choose a template for custom analysis' "
                                "--layout reverse --border "
                                "--prompt '📄 Template > ' "
                                "--preview 'cat $(echo {} | cut -d\"|\" -f3 | xargs) | head -50' "
                                "--preview-window 'right:60%' "
                                "2> /dev/tty"))
          _ (.delete temp-file)]
      (when (zero? (:exit result))
        (str/trim (:out result))))
    (catch Exception e
      (println (colorize :red (str "Error selecting template: " (.getMessage e))))
      nil)))

;; =============================================================================
;; Input Gathering
;; =============================================================================

(defn- get-user-input
  "Prompt user for input value"
  [input-name default-val required?]
  (if default-val
    (do
      (print (str "  🔹 " input-name " [default: " default-val "]: "))
      (flush)
      (let [user-val (read-line)]
        (if (str/blank? user-val)
          default-val
          user-val)))
    (do
      (print (str "  🔸 " input-name (when required? " (required)") ": "))
      (flush)
      (read-line))))

(defn- gather-inputs
  "Gather all required inputs for an agent"
  [agent-id agent-meta project-dir reports-dir timestamp]
  (let [inputs (:inputs agent-meta [])
        ;; Convert keywords to strings
        input-strs (map name inputs),
        ;; Always ensure output-file is included
        all-inputs (if (some #(= "output-file" %) input-strs)
                     input-strs
                     (conj input-strs "output-file"))
        json-inputs (atom {:project-dir project-dir})]

    ;; Special handling for custom-template-analyzer
    (when (= agent-id :custom-template-analyzer)
      (println "\n📝 Select an analysis template:")
      (let [templates (list-templates project-dir)]
        (if (seq templates)
          (let [templates-text (format-templates-for-fzf templates)
                selection (run-fzf-template-select templates-text)]
            (if selection
              (let [filename (str/trim (first (str/split selection #"\|")))
                    template (first (filter #(= (:filename %) filename) templates))]
                (if template
                  (do
                    (swap! json-inputs assoc :prompt (str "file://" (:path template)))
                    (println "✅ Template loaded:" (:filename template) "\n"))
                  (do
                    (println "❌ Template not found:" filename)
                    (throw (ex-info "Template not found" {:filename filename})))))
              (do
                (println "❌ No template selected.")
                (throw (ex-info "Template selection cancelled" {})))))
          (println "⚠️  No templates found in any template directories"))))

    (println "📋 Configuration:")

    ;; Gather other inputs
    (doseq [input all-inputs]
      ;; Skip internal/handled inputs
      (when-not (or (= input "project-dir")
                    (str/includes? input ".")
                    (str/includes? input "[")                    (str/starts-with? input "last-obs")
                    (str/starts-with? input "trace")
                    (and (= agent-id :custom-template-analyzer)
                         (= input "prompt")))
        (let [default (when (= input "output-file")
                        (str reports-dir "/" (name agent-id) "-" timestamp ".md"))
              value (get-user-input input default true)]
          (when-not (str/blank? value)
            (swap! json-inputs assoc (keyword input) value)))))

    @json-inputs))

;; =============================================================================
;; Execution
;; =============================================================================

(defn- save-to-history
  "Save analysis run to history database"
  [project-dir agent-id output-file duration]
  (let [db-file (str (System/getProperty "user.home") "/.config/codebase-analyzer/index.jsonl")
        db-dir (io/file (System/getProperty "user.home") ".config/codebase-analyzer")]
    (.mkdirs db-dir)

    (try
      (let [abs-project-path (.getAbsolutePath (io/file project-dir))
            timestamp (java.time.Instant/now)
            entry {:generated_at (str timestamp)
                   :duration_sec duration
                   :project abs-project-path
                   :agent agent-id
                   :report_path output-file}
            json-line (json/generate-string entry)]
        (spit db-file (str json-line "\n") :append true)
        (println "📚 Added to history database"))
      (catch Exception e
        (println (colorize :yellow (str "⚠️  Could not save to history: " (.getMessage e))))))))

(defn- post-analysis-menu
  "Show post-analysis options menu"
  [output-file]
  (loop []
    (println)
    (print "Press ENTER to continue (new analysis), 'v' to view (less), 'o' to open, or 'q' to quit: ")
    (flush)
    (let [choice (read-line)]
      (cond
        (str/blank? choice)
        :continue

        (= choice "v")
        (if (and output-file (.exists (io/file output-file)))
          (do
            (.waitFor (.start (doto (ProcessBuilder. ["less" output-file])
                                (.inheritIO))))
            (recur))
          (do
            (println "❌ Report file not found.")
            (recur)))

        (= choice "o")
        (if (and output-file (.exists (io/file output-file)))
          (do
            (println "📄 Opening report...")
            (shell/sh "open" output-file)
            (recur))
          (do
            (println "❌ Report file not found.")
            (recur)))

        (= choice "q")
        :quit

        :else
        (recur)))))

;; =============================================================================
;; Main Runner
;; =============================================================================

(defn run-smart-analyzer
  "Run the interactive smart analyzer.
   
   Options:
   - :project-dir - Project directory to analyze (default: current directory)
   - :reports-dir - Directory to save reports (default: ./reports)"
  [& {:keys [project-dir reports-dir]
      :or {project-dir "."
           reports-dir  nil}}]

  (check-dependencies)
  (print-banner)

  (let [project-dir (-> (io/file project-dir) .getAbsolutePath)
        reports-dir (or reports-dir (str project-dir "/reports"))
        reports-dir-file (io/file reports-dir)]
    (.mkdirs reports-dir-file)

    (println (colorize :cyan (str "📁 Target: " project-dir)))
    (println)

    ;; Main loop
    (loop []
      (println "🔍 Search for an agent (Type to search, ENTER to select, ESC to exit)")

      ;; Load agents from both local and registry sources
      (let [agents (get-all-agents)
            _  (println "📝 Generating previews...")
            preview-dir (write-preview-files agents)
            agents-text (format-agents-for-fzf agents)
            selection (run-fzf-agent-select agents-text preview-dir)]

        (if selection
          (do
            (let [parsed (parse-agent-selection selection)
                  agent-id (keyword (:id parsed))
                  agent-desc (:description parsed)
                  ;; Find the agent to check its source
                  selected-agent (first (filter #(= (:id %) agent-id) agents))
                  is-registry-agent? (= (:source selected-agent) :registry)]

              ;; If it's a registry agent, load it into the runtime registry
              (when is-registry-agent?
                (let [{:keys [id spec]} (registry/lookup-agent (name agent-id))]
                  (swap! pyjama/agents-registry assoc id spec)
                  (println (colorize :cyan (str "📦 Loaded from registry: " (name id))))))

              (println)
              (println "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
              (println (colorize :cyan (str "🤖 Selected: " (name agent-id))))
              (println (colorize :gray (str "📝 Description: " agent-desc)))
              (println "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
              (println)

              ;; Show visualization
              (try
                (when-let [meta (pyjama/describe-agent agent-id)]
                  (let [spec (:spec meta)
                        registry @pyjama/agents-registry
                        common-steps (:common-steps registry)
                        merged-steps (merge common-steps (:steps spec))
                        full-spec (assoc spec :steps merged-steps)]
                    (agent/visualize agent-id full-spec)))
                (catch Exception e
                  (println (colorize :yellow (str "⚠️  Could not visualize agent: " (.getMessage e))))))
              (println)

              ;; Gather inputs
              ;; Gather inputs and execute
              (try
                (let [agent-meta (pyjama/describe-agent agent-id)
                      timestamp (.format (java.time.LocalDateTime/now)
                                         (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd-HHmm"))
                      inputs (gather-inputs agent-id agent-meta project-dir reports-dir timestamp)
                      output-file (get inputs :output-file)]

                  (println)
                  (println "🚀 Ready to launch!")
                  (println (str "   Agent: " (name agent-id)))
                  (println (str "   Inputs: " (json/generate-string inputs)))
                  (println)

                  (print "Press ENTER to start (or Ctrl+C to cancel)...")
                  (flush)
                  (read-line)

                  ;; Execute agent
                  (let [start-time (System/currentTimeMillis)]
                    (try
                      (agent/call (assoc inputs :id agent-id))
                      (let [end-time (System/currentTimeMillis)
                            duration (quot (- end-time start-time) 1000)]
                        (println)
                        (println (colorize :green (str "✅ Agent completed successfully in " duration "s")))

                        ;; Agent handles its own file saving
                        ;; Just confirm file exists and save to history
                        (when (and output-file (.exists (io/file output-file)))
                          (println (colorize :cyan (str "💾 Report saved to: " output-file)))
                          (save-to-history project-dir (name agent-id) output-file duration))

                        ;; Post-analysis menu
                        (let [action (post-analysis-menu output-file)]
                          (case action
                            :quit (do
                                    (println "👋 Exiting.")
                                    (System/exit 0))
                            :continue nil ;; Continue to recur
                            nil)))

                      (catch Exception e
                        (println (colorize :red "❌ Agent failed."))
                        (println (colorize :red (str "   Error: " (.getMessage e))))
                        (when (System/getProperty "debug")
                          (.printStackTrace e))))))

                (catch clojure.lang.ExceptionInfo e
                  ;; Template selection cancelled or other recoverable error
                  (println)
                  (println (colorize :yellow "Returning to agent selection..."))
                  (println))

                (catch Exception e
                  (println (colorize :red (str "❌ Error gathering inputs: " (.getMessage e))))
                  (println)))

              ;; Always continue loop after processing
              (recur)))

          ;; No selection - exit
          (do
            (println "👋 Exiting.")
            (System/exit 0)))))))
