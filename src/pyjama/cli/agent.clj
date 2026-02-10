(ns pyjama.cli.agent
  (:require

   ; make sure tools are kept
   [pyjama.tools.reachability]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cheshire.core :as json]
   [pyjama.agent.core :as agent]
   [pyjama.core :as pyjama]
   [pyjama.runner :as runner]
   [pyjama.cli.inspect :as inspect]
   [pyjama.cli.search :as search]
   [pyjama.cli.registry :as registry])
  (:gen-class))

(defn load-agents-from-directory
  "Load all .edn files from a directory and merge them"
  [dir-path]
  (let [dir (io/file dir-path)]
    (when (.isDirectory dir)
      (let [edn-files (->> (.listFiles dir)
                           (filter #(and (.isFile %)
                                         (str/ends-with? (.getName %) ".edn")))
                           (sort-by #(.getName %)))]
        (when (seq edn-files)
          (println (str "📂 Loading " (count edn-files) " agent file(s) from: " dir-path))
          (reduce
           (fn [acc file]
             (try
               (let [content (slurp file)
                     data (read-string content)
                     agent-name (str/replace (.getName file) #"\.edn$" "")

                     ;; Use shared normalization logic from pyjama.core
                     result (pyjama/normalize-agent-data data agent-name)

                     ;; Log what we loaded
                     single-agent? (contains? data :steps)]

                 (if single-agent?
                   (println (str "  ✓ Loaded: " (.getName file) " as single agent '" agent-name "'"))
                   (println (str "  ✓ Loaded: " (.getName file) " with " (count data) " agent(s)")))

                 (merge acc result))
               (catch Exception e
                 (println (str "  ✗ Error loading " (.getName file) ": " (.getMessage e)))
                 acc)))
           {}
           edn-files))))))

(defn load-agents-config []
  (let [path (System/getProperty "agents.edn")]
    (if path
      ;; Support comma-separated directories/files
      (let [paths (str/split path #",")
            merged-config (reduce
                           (fn [acc p]
                             (let [trimmed (str/trim p)
                                   file (io/file trimmed)]
                               (cond
                                 ;; Directory: load all .edn files
                                 (.isDirectory file)
                                 (merge acc (load-agents-from-directory trimmed))

                                 ;; File: load single file
                                 (.exists file)
                                 (let [data (read-string (slurp file))
                                       agent-name (str/replace (.getName file) #"\.edn$" "")
                                       single-agent? (contains? data :steps)]
                                   (println (str "📄 Loading agents from: " trimmed))
                                   (when single-agent?
                                     (println (str "  ✓ Detected single agent format, registered as '" agent-name "'")))
                                   ;; Use shared normalization logic
                                   (merge acc (pyjama/normalize-agent-data data agent-name)))

                                 ;; Not found
                                 :else
                                 (do
                                   (println (str "⚠️  Path not found: " trimmed))
                                   acc))))
                           {}
                           paths)]
        (when (seq merged-config)
          merged-config))

      ;; Fallback to resource if no system property
      (when-let [res (io/resource "agents.edn")]
        (System/setProperty "agents.edn" (.getPath res))))))

;; Initialize on namespace load if not explicitly set
(load-agents-config)

(defn- run-init-hooks!
  "Run initialization hooks.
  
  1. First runs Pyjama's default initialization (logging, metrics, shared metrics)
  2. Then runs project-specific initialization if available
  
  This is useful for registering hooks, setting up integrations, etc."
  []
  ;; 1. Run Pyjama's default initialization
  (try
    (require '[pyjama.agent.init :as pyjama-init])
    (let [init-fn (resolve 'pyjama.agent.init/init!)]
      (init-fn))
    (catch Exception e
      ;; Default init is optional, log and continue
      (binding [*out* *err*]
        (println "⚠️  Pyjama default initialization failed:" (.getMessage e)))))

  ;; 2. Run project-specific initialization (if available)
  (try
    (when-let [init-ns (or (System/getProperty "pyjama.init.ns")
                           ;; Auto-detect common init namespaces
                           (some #(try
                                    (require %)
                                    (when (ns-resolve % 'init!)
                                      %)
                                    (catch Exception _ nil))
                                 ['codebase-analyzer.init
                                  'project.init
                                  'app.init]))]
      (when-let [init-fn (ns-resolve init-ns 'init!)]
        (println (str "🔧 Running project initialization from " init-ns))
        (init-fn)))
    (catch Exception e
      ;; Project initialization is optional, so just log and continue
      (binding [*out* *err*]
        (println "⚠️  Project initialization hook failed:" (.getMessage e))))))

;; Run initialization hooks after loading config
(run-init-hooks!)

(defn get-runtime-overrides []
  (let [env-impl (or (System/getenv "PYJAMA_IMPL") (System/getenv "LLM_PROVIDER"))
        env-model (or (System/getenv "LLM_MODEL") (System/getenv "OLLAMA_MODEL"))]
    (cond-> {}
      env-impl (assoc :impl (keyword env-impl))
      env-model (assoc :model env-model))))

(defn exec-agent [params]
  (agent/call (merge params (get-runtime-overrides))))

(defn print-banner []
  (println "\n╔════════════════════════════════════════════════════════════════╗")
  (println "║                                                                ║")
  (println "║                  🤖 PYJAMA AGENTS 🤖                           ║")
  (println "║                                                                ║")
  (println "║         Intelligent Multi-Agent Framework                     ║")
  (println "║                                                                ║")
  (println "╚════════════════════════════════════════════════════════════════╝\n"))

(defn print-help []
  (println "
Pyjama CLI - Agent Framework
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

USAGE:
  clj -M:pyjama [command] [args...]

COMMANDS:

  Interactive & Core:
    (no args)           Interactive menu - select from all available tools
    smart [dir] [out]   Smart analyzer - interactive agent selection
                        - dir: project directory (default: current)
                        - out: reports directory (default: <dir>/reports)
    
  System Tools:
    inspect [check]     System health check and agent listing
    reports             Browse and search analysis report history
    history             (alias for reports)
    list                List all available agents (JSON)
    search <query>      Search agents by keyword
    describe <id>       Show detailed agent information
    visualize <id>      Display agent workflow diagram (ASCII)
    visualize-mermaid <id> [file]
                        Generate Mermaid flowchart diagram
                        - file: optional output file (default: stdout)
    
  Analysis Modes:
    comprehensive <dir> [out]
                        Run all analysis types in parallel
                        
    deep-dive <dir> <focus> [out]
                        Focus on specific aspect
                        Focus: security, performance, testing, documentation, refactoring
                        
    compare <dir-a> <dir-b>
                        Compare two codebases side-by-side
                        
    feature <dir> <name> [out]
                        Generate feature documentation
    
  Advanced:
    run <agent-id> <json-inputs>
                        Execute any agent programmatically
    
    registry <command>  Manage local agent registry
                        Commands: register, list, lookup, remove/unregister/delete
                        See: clj -M:pyjama registry (for details)
    
    lookup-run <agent-id> <json-inputs>
                        Look up agent from registry and execute it
    help                Show this help

EXAMPLES:

  # Interactive menu
  clj -M:pyjama

  # Smart analyzer on current directory
  clj -M:pyjama smart

  # Smart analyzer on specific project
  clj -M:pyjama smart ~/projects/my-app

  # Browse previous reports
  clj -M:pyjama reports

  # System check
  clj -M:pyjama inspect

  # Search for agents
  clj -M:pyjama search architecture

  # Comprehensive analysis
  clj -M:pyjama comprehensive . analysis-report.md

  # Deep dive into security
  clj -M:pyjama deep-dive . security security-audit.md

  # Compare codebases
  clj -M:pyjama compare ./old-version ./new-version

  # Run specific agent
  clj -M:pyjama run project-purpose-analyzer '{\"project-dir\":\".\",\"output-file\":\"report.md\"}'

For more information, visit: https://github.com/hellonico/pyjama
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"))

(defn run-comprehensive-analysis
  "Run comprehensive codebase analysis"
  [project-dir & [output-file]]
  (println "\n🚀 Starting Comprehensive Analysis...")
  (println "📁 Project:" project-dir)
  (when output-file
    (println "💾 Output file:" output-file))
  (println "\n⏳ This may take a few minutes...\n")

  (let [params (cond-> {:id :codebase-analyzer
                        :project-dir project-dir}
                 output-file (assoc :output-file output-file))
        result (exec-agent params)]
    (println "\n✅ Analysis complete!")
    result))

(defn run-deep-dive-analysis
  "Run focused deep-dive analysis"
  [project-dir focus & [output-file]]
  (println "\n🔬 Starting Deep Dive Analysis...")
  (println "📁 Project:" project-dir)
  (println "🎯 Focus:" focus)
  (when output-file
    (println "💾 Output file:" output-file))
  (println "\n⏳ Analyzing...\n")

  (let [params (cond-> {:id :deep-dive-analyzer
                        :project-dir project-dir
                        :focus focus}
                 output-file (assoc :output-file output-file))
        result (exec-agent params)]
    (println "\n✅ Deep dive complete!")
    result))

(defn run-comparative-analysis
  "Compare two codebases"
  [project-a project-b]
  (println "\n⚖️  Starting Comparative Analysis...")
  (println "📁 Project A:" project-a)
  (println "📁 Project B:" project-b)
  (println "\n⏳ Analyzing both projects...\n")

  (let [result (exec-agent {:id :comparative-analyzer
                            :project-a project-a
                            :project-b project-b})]
    (println "\n✅ Comparison complete!")
    result))

(defn find-template
  "Find template file from multiple locations (user folders, then built-in resources)"
  [template-name project-dir]
  (let [template-filename (if (str/ends-with? template-name ".md") template-name (str template-name ".md"))
        user-home (System/getProperty "user.home")

        ;; Check these locations in order
        locations [(str user-home "/.codebase-analyzer/templates/" template-filename)
                   (str project-dir "/user-templates/" template-filename)
                   (str "analysis-templates/" template-filename)]]

    ;; Try file system locations first
    (or (some (fn [path]
                (let [f (io/file path)]
                  (when (.exists f)
                    {:source :file :path path :content (slurp f)})))
              (take 2 locations))

        ;; Then try classpath resource
        (when-let [resource (io/resource (last locations))]
          {:source :resource :path (last locations) :content (slurp resource)}))))

(defn run-custom-analysis
  "Run analysis using a custom user template"
  [project-dir template-name & [output-file]]
  (println "\n🎨 Starting Custom Analysis...")
  (println "📁 Project:" project-dir)
  (println "📝 Template:" template-name)

  (if-let [{:keys [source path content]} (find-template template-name project-dir)]
    (do
      (println "📄 Loaded template:" path (str "(" (name source) ")"))
      (println "\n⏳ Analyzing...\n")
      (let [params (cond-> {:id :custom-template-analyzer
                            :project-dir project-dir
                            :template-name template-name
                            :prompt content}
                     output-file (assoc :output-file output-file))
            result (exec-agent params)]
        (println "\n✅ Analysis complete!")
        result))

    (do
      (println "\n❌ Template not found:" template-name)
      (println "\nSearched in:")
      (println "  1. ~/.codebase-analyzer/templates/")
      (println (str "  2. " project-dir "/user-templates/"))
      (println "  3. resources/analysis-templates/")
      (System/exit 1))))

(defn run-agent-execution
  "Run a specific agent by ID"
  [project-dir agent-id & [output-file]]
  (println (str "\n🤖 Running Agent: " agent-id "..."))
  (println "📁 Project:" project-dir)
  (when output-file
    (println "💾 Output file:" output-file))
  (println "\n⏳ Executing...\n")

  (let [params (cond-> {:id (keyword agent-id)
                        :project-dir project-dir}
                 output-file (assoc :output-file output-file))
        result (exec-agent params)]
    (println "\n✅ Agent execution complete!")
    result))

(defn run-feature-documentation
  "Run feature documentation generation"
  [project-dir feature-name & [output-file]]
  (println "\n📚 Starting Feature Documentation...")
  (println "📁 Project:" project-dir)
  (println "✨ Feature:" feature-name)
  (when output-file
    (println "💾 Output file:" output-file))
  (println "\n⏳ Analyzing and documenting...\n")

  (let [params (cond-> {:id :feature-documenter
                        :project-dir project-dir
                        :feature-name feature-name}
                 output-file (assoc :output-file output-file))
        result (exec-agent params)]
    (println "\n✅ Documentation generated!")
    result))

(defn run-list-agents
  "List all agents in JSON format for the shell script"
  []
  (let [agents (pyjama/list-agents)
        ;; Minimize for shell script listing
        minimal (map #(select-keys % [:id :description :type]) agents)]
    (println (json/generate-string minimal {:pretty true}))))

(defn run-search-agents
  "Search agents and output JSON"
  [query]
  (let [agents (pyjama/search-agents query)
        minimal (map #(select-keys % [:id :description :type]) agents)]
    (println (json/generate-string minimal {:pretty true}))))

(defn run-describe-agent
  "Describe an agent inputs/outputs in JSON"
  [id]
  (if-let [meta (pyjama/describe-agent (keyword id))]
    (println (json/generate-string meta {:pretty true}))
    (do
      (println (json/generate-string {:error "Agent not found" :id id}))
      (System/exit 1))))

(defn run-visualize-agent
  "Generate a simple ASCII flow diagram for an agent"
  [id]
  (if-let [meta (pyjama/describe-agent (keyword id))]
    (let [spec (:spec meta)
          ;; Merge common steps before visualization to show full flow
          registry @pyjama/agents-registry
          common-steps (:common-steps registry)
          merged-steps (merge common-steps (:steps spec))
          full-spec (assoc spec :steps merged-steps)]
      (agent/visualize id full-spec))
    (do
      (println (str "❌ Agent not found: " id))
      (System/exit 1))))

(defn run-visualize-mermaid
  "Generate a Mermaid flowchart diagram for an agent"
  [id & [output-file]]
  (if-let [meta (pyjama/describe-agent (keyword id))]
    (let [spec (:spec meta)
          registry @pyjama/agents-registry
          common-steps (:common-steps registry)
          merged-steps (merge common-steps (:steps spec))
          full-spec (assoc spec :steps merged-steps)]
      (require '[pyjama.agent.visualize :as viz])
      (let [diagram ((resolve 'viz/visualize-mermaid) id full-spec)]
        (if output-file
          (do
            (spit output-file diagram)
            (println (str "✅ Mermaid diagram saved to: " output-file)))
          (println diagram))))
    (do
      (println (str "❌ Agent not found: " id))
      (System/exit 1))))

(defn run-generic-execution
  "Run any agent with a map of inputs passed as JSON-encoded string or key=value pairs"
  [agent-id & args]
  (let [inputs (if (= 1 (count args))
                 (try
                   (json/parse-string (first args) true)
                   (catch Exception _
                     (throw (ex-info "Input must be a JSON string" {:input (first args)}))))
                 ;; transform proper key value pairs
                 (->> (partition 2 args)
                      (map (fn [[k v]] [(keyword k) v]))
                      (into {})))

        ;; Look up the agent in the registry to get its :name
        agent-key (keyword agent-id)
        agent-spec (get @pyjama.core/agents-registry agent-key)
        actual-agent-name (or (:name agent-spec) agent-id)]

    (println (str "\n🤖 Running Agent: " agent-id))
    (when (and agent-spec (not= agent-id actual-agent-name))
      (println (str "   Name: " actual-agent-name)))
    (println "📥 Inputs:" inputs)
    (println "\n⏳ Executing...\n")

    ;; Set system property for shared metrics tracking
    (System/setProperty "pyjama.agent.id" actual-agent-name)

    ;; Register shutdown hook to mark agent as complete on Ctrl-C
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (try
                                   (when-let [complete-fn (resolve 'pyjama.agent.hooks.shared-metrics/record-agent-complete!)]
                                     (complete-fn actual-agent-name))
                                   (catch Exception _ nil)))))

    (let [params (assoc inputs :id agent-key)
          result (exec-agent params)]
      (println "\n✅ Agent execution complete!")
      result)))

(defn run-lookup-execution
  "Look up an agent from the registry and execute it with provided inputs"
  [agent-id & args]
  (let [inputs (if (= 1 (count args))
                 (try
                   (json/parse-string (first args) true)
                   (catch Exception _
                     (throw (ex-info "Input must be a JSON string" {:input (first args)}))))
                 ;; transform proper key value pairs
                 (->> (partition 2 args)
                      (map (fn [[k v]] [(keyword k) v]))
                      (into {})))]

    ;; Look up the agent from the registry
    (let [{:keys [id spec]} (registry/lookup-agent agent-id)
          actual-agent-name (or (:name spec) (name id))]

      (println "📥 Inputs:" inputs)
      (println "\n⏳ Executing...\n")

      ;; Set system property for shared metrics tracking
      (System/setProperty "pyjama.agent.id" actual-agent-name)

      ;; Register shutdown hook to mark agent as complete on Ctrl-C
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. (fn []
                                   (try
                                     (when-let [complete-fn (resolve 'pyjama.agent.hooks.shared-metrics/record-agent-complete!)]
                                       (complete-fn actual-agent-name))
                                     (catch Exception _ nil)))))

      ;; Temporarily register the agent in the runtime registry
      (swap! pyjama.core/agents-registry assoc id spec)

      ;; Execute the agent
      (let [params (assoc inputs :id id)
            result (exec-agent params)]
        (println "\n✅ Agent execution complete!")
        result))))
;; =============================================================================

(def ^:private colors
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

(def ^:private menu-options
  [{:key "1"
    :label "🚀 Smart Analyzer"
    :description "Interactive agent selection and execution"
    :action #(runner/run-smart-analyzer)}

   {:key "2"
    :label "🔍 Search Reports"
    :description "Browse and search analysis report history"
    :action #(search/-main)}

   {:key "3"
    :label "🔧 Inspect System"
    :description "Check system health and list agents"
    :action #(inspect/-main "check")}

   {:key "4"
    :label "📋 List Agents"
    :description "List all available agents (JSON)"
    :action #(run-list-agents)}

   {:key "5"
    :label "🔎 Search Agents"
    :description "Search agents by keyword"
    :action (fn []
              (print "Enter search query: ")
              (flush)
              (let [query (read-line)]
                (when-not (str/blank? query)
                  (run-search-agents query))))}

   {:key "q"
    :label "👋 Quit"
    :description "Exit pyjama CLI"
    :action #(do
               (println (colorize :gray "Goodbye!"))
               (System/exit 0))}])

(defn- print-menu-banner
  "Print the main CLI banner"
  []
  (println)
  (println "╔════════════════════════════════════════════════════════════════╗")
  (println "║                                                                ║")
  (println "║        🧠 PYJAMA - Agent Framework CLI 🧠                      ║")
  (println "║                                                                ║")
  (println "║  Interactive Analysis • Report Management • System Tools       ║")
  (println "║                                                                ║")
  (println "╚════════════════════════════════════════════════════════════════╝")
  (println))

(defn- print-main-menu
  "Print the interactive menu"
  []
  (println (colorize :cyan "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
  (println (colorize :white "Main Menu"))
  (println (colorize :cyan "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
  (println)
  (doseq [option menu-options]
    (println (str "  " (colorize :green (:key option)) " - "
                  (:label option)))
    (println (str "      " (colorize :gray (:description option))))
    (println))
  (println (colorize :cyan "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
  (println))

(defn- run-interactive-menu
  "Run the interactive menu loop"
  []
  (print-menu-banner)
  (loop []
    (print-main-menu)
    (print (colorize :yellow "Select option [1-5, q]: "))
    (flush)
    (let [choice (str/trim (read-line))
          option (first (filter #(= (:key %) choice) menu-options))]
      (if option
        (do
          (println)
          (try
            ((:action option))
            (catch Exception e
              (println)
              (println (colorize :red (str "❌ Error: " (.getMessage e))))
              (when (System/getProperty "debug")
                (.printStackTrace e))))
          (println)
          (println (colorize :gray "Press ENTER to continue..."))
          (read-line)
          (recur))
        (do
          (println (colorize :yellow "Invalid option. Please try again."))
          (recur))))))


(defn -main [& args]
  (let [[mode & params] args
        json-mode? #{"list" "search" "describe"}]

    (when-not (json-mode? mode)
      (print-banner))

    (if (empty? args)
      (run-interactive-menu)
      (try
        (case mode
          "comprehensive" (apply run-comprehensive-analysis params)
          "deep-dive" (apply run-deep-dive-analysis params)
          "compare" (apply run-comparative-analysis params)
          "custom" (apply run-custom-analysis params)
          "agent" (apply run-agent-execution params)
          "feature" (apply run-feature-documentation params)

          ;; Interactive agent registry commands
          "list" (run-list-agents)
          "search" (apply run-search-agents params)
          "describe" (apply run-describe-agent params)
          "visualize" (apply run-visualize-agent params)
          "visualize-mermaid" (apply run-visualize-mermaid params)
          "run" (apply run-generic-execution params)

          ;; Registry commands
          "registry" (apply registry/-main params)
          "lookup-run" (apply run-lookup-execution params)

          ;; Interactive smart analyzer
          "smart" (apply runner/run-smart-analyzer
                         (concat
                          (when (first params) [:project-dir (first params)])
                          (when (second params) [:reports-dir (second params)])))

          ;; System tools
          "inspect" (apply inspect/-main (or params ["check"]))
          "reports" (apply search/-main params)
          "history" (apply search/-main params)

          "help" (print-help)
          (do
            (println "❌ Unknown mode:" mode)
            (print-help)))
        (catch Exception e
          (if (json-mode? mode)
            (println (json/generate-string {:error (.getMessage e)}))
            (do
              (println "\n❌ Error during analysis:")
              (println (.getMessage e))
              (when (System/getProperty "debug")
                (.printStackTrace e))))
          (System/exit 1))))

    (System/exit 0)))