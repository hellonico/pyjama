(ns pyjama.cli.agent
  (:require

   ; make sure tools are kept
   [pyjama.tools.reachability]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cheshire.core :as json]
   [pyjama.agent.core :as agent]
   [pyjama.core :as pyjama])
  (:gen-class))

(defn load-agents-config []
  (let [path (System/getProperty "agents.edn")]
    (when-not (and path (.exists (io/file path)))
      (let [res (io/resource "agents.edn")]
        (when res
          (System/setProperty "agents.edn" (.getPath res)))))))

;; Initialize on namespace load if not explicitly set
(load-agents-config)

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
  (println "║        🔍 PYJAMA CODEBASE ANALYZER 🔍                         ║")
  (println "║                                                                ║")
  (println "║  Intelligent Multi-Agent System for Code Analysis             ║")
  (println "║                                                                ║")
  (println "╚════════════════════════════════════════════════════════════════╝\n"))

(defn print-help []
  (println "
Available Analysis Modes:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. COMPREHENSIVE ANALYSIS
   Runs all analysis types in parallel and synthesizes findings
   
   Usage:
   clj -M:comprehensive <project-dir> [output-file]
   
   Example:
   clj -M:comprehensive . analysis-report.md

2. DEEP DIVE ANALYSIS
   Focuses on a specific aspect of the codebase
   
   Usage:
   clj -M:deep-dive <project-dir> <focus> [output-file]
   
   Focus areas: security, performance, testing, documentation, refactoring
   
   Example:
   clj -M:deep-dive . security security-audit.md

3. COMPARATIVE ANALYSIS
   Compares two codebases side-by-side
   
   Usage:
   clj -M:compare <project-a> <project-b>
   
   Example:
   clj -M:compare ./old-version ./new-version

4. FEATURE DOCUMENTATION
   Generates extensive documentation for a specific feature
   
   Usage:
   clj -M:run feature <project-dir> <feature-name> [output-file]
   
   Example:
   clj -M:run feature . \"User Authentication\" auth-guide.md

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
                      (into {})))]

    (println (str "\n🤖 Running Generic Agent: " agent-id))
    (println "📥 Inputs:" inputs)
    (println "\n⏳ Executing...\n")

    (let [params (assoc inputs :id (keyword agent-id))
          result (exec-agent params)]
      (println "\n✅ Agent execution complete!")
      result)))

(defn -main [& args]
  (let [[mode & params] args
        json-mode? #{"list" "search" "describe"}]

    (when-not (json-mode? mode)
      (print-banner))

    (if (empty? args)
      (print-help)
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
          "run" (apply run-generic-execution params)

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

    (when-not (json-mode? mode)
      (println "\n👋 Analysis session ended.\n"))
    (System/exit 0)))