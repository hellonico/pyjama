(ns pyjama.cli.registry
  "Agent registry management - register and lookup agents"
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [pyjama.core :as pyjama])
  (:gen-class))

;; =============================================================================
;; Registry Configuration
;; =============================================================================

(def ^:private registry-dir
  "Local agent registry directory"
  (str (System/getProperty "user.home") "/.pyjama/registry"))

(defn- ensure-registry-dir!
  "Ensure the registry directory exists"
  []
  (let [dir (io/file registry-dir)]
    (when-not (.exists dir)
      (.mkdirs dir))
    dir))

(defn- agent-file-path
  "Get the file path for an agent in the registry"
  [agent-id]
  (str registry-dir "/" (name agent-id) ".edn"))

;; =============================================================================
;; Registry Operations
;; =============================================================================

(defn register-agent!
  "Register an agent definition to the local registry.
  
  Parameters:
    agent-file - Path to the agent EDN file to register
    
  The agent will be stored in ~/.pyjama/registry/ with its ID as the filename."
  [agent-file]
  (ensure-registry-dir!)

  (let [file (io/file agent-file)]
    (when-not (.exists file)
      (throw (ex-info (str "Agent file not found: " agent-file)
                      {:file agent-file})))

    ;; Load and validate the agent definition
    (let [agent-data (edn/read-string (slurp file))
          ;; Extract agent name from filename
          agent-name (str/replace (.getName file) #"\.edn$" "")
          ;; Normalize to handle both single and multi-agent formats
          normalized (pyjama/normalize-agent-data agent-data agent-name)

          ;; Get the first (or only) agent from normalized data
          [agent-id agent-spec] (first normalized)]

      ;; Validate it has required fields
      (when-not (:description agent-spec)
        (println (str "⚠️  Warning: Agent has no :description field")))

      (when-not (or (:steps agent-spec) (:prompt agent-spec))
        (throw (ex-info "Agent must have either :steps (graph agent) or :prompt (simple agent)"
                        {:agent-id agent-id})))

      ;; Write to registry
      (let [registry-file (agent-file-path agent-id)]
        (spit registry-file (pr-str {agent-id agent-spec}))

        (println (str "✅ Agent registered successfully!"))
        (println (str "   ID: " agent-id))
        (println (str "   Description: " (or (:description agent-spec) "N/A")))
        (println (str "   Type: " (if (:steps agent-spec) "graph" "simple")))
        (println (str "   Location: " registry-file))

        {:agent-id agent-id
         :file registry-file
         :spec agent-spec}))))

(defn list-registered-agents
  "List all agents in the local registry"
  []
  (ensure-registry-dir!)

  (let [dir (io/file registry-dir)
        edn-files (->> (.listFiles dir)
                       (filter #(and (.isFile %)
                                     (str/ends-with? (.getName %) ".edn")))
                       (sort-by #(.getName %)))]

    (if (empty? edn-files)
      (do
        (println "📂 Registry is empty")
        (println (str "   Location: " registry-dir))
        [])

      (do
        (println (str "📂 Registered Agents (" (count edn-files) " total)"))
        (println (str "   Location: " registry-dir))
        (println)

        (mapv (fn [file]
                (try
                  (let [data (edn/read-string (slurp file))
                        [agent-id agent-spec] (first data)]
                    (println (str "  • " agent-id))
                    (println (str "    Description: " (or (:description agent-spec) "N/A")))
                    (println (str "    Type: " (if (:steps agent-spec) "graph" "simple")))
                    (println)
                    {:id agent-id
                     :description (:description agent-spec)
                     :type (if (:steps agent-spec) :graph :simple)
                     :file (.getAbsolutePath file)})
                  (catch Exception e
                    (println (str "  ✗ Error loading " (.getName file) ": " (.getMessage e)))
                    nil)))
              edn-files)))))

(defn lookup-agent
  "Look up an agent from the registry and return its specification.
   If json-output? is true, prints JSON instead of human-readable format."
  ([agent-id] (lookup-agent agent-id false))
  ([agent-id json-output?]
   (let [agent-key (keyword agent-id)
         registry-file (agent-file-path agent-key)]

     (when-not (.exists (io/file registry-file))
       (throw (ex-info (str "Agent not found in registry: " agent-id)
                       {:agent-id agent-id
                        :registry-dir registry-dir
                        :searched-file registry-file})))

     (let [data (edn/read-string (slurp registry-file))
           [_ agent-spec] (first data)
           result {:id agent-key
                   :spec agent-spec}]

       (if json-output?
         ;; JSON output for programmatic use
         (println (cheshire.core/generate-string result))
         ;; Human-readable output
         (do
           (println (str "🔍 Found agent: " agent-id))
           (println (str "   Description: " (or (:description agent-spec) "N/A")))
           (println (str "   Type: " (if (:steps agent-spec) "graph" "simple")))
           (println)))

       result))))

(defn remove-agent!
  "Remove an agent from the registry"
  [agent-id]
  (let [agent-key (keyword agent-id)
        registry-file (agent-file-path agent-key)
        file (io/file registry-file)]

    (if (.exists file)
      (do
        (.delete file)
        (println (str "✅ Agent removed from registry: " agent-id))
        {:agent-id agent-key
         :removed true})
      (do
        (println (str "⚠️  Agent not found in registry: " agent-id))
        {:agent-id agent-key
         :removed false}))))

;; =============================================================================
;; CLI Entry Points
;; =============================================================================

(defn -main
  "CLI entry point for registry commands"
  [& args]
  (try
    (let [[command & params] args]
      (case command
        "register"
        (if-let [agent-file (first params)]
          (register-agent! agent-file)
          (do
            (println "❌ Usage: register <agent-file.edn>")
            (System/exit 1)))

        "list"
        (list-registered-agents)

        "lookup"
        (if-let [agent-id (first params)]
          (lookup-agent agent-id)
          (do
            (println "❌ Usage: lookup <agent-id>")
            (System/exit 1)))

        "remove"
        (if-let [agent-id (first params)]
          (remove-agent! agent-id)
          (do
            (println "❌ Usage: remove <agent-id>")
            (System/exit 1)))

        ;; Aliases for remove
        "unregister"
        (if-let [agent-id (first params)]
          (remove-agent! agent-id)
          (do
            (println "❌ Usage: unregister <agent-id>")
            (System/exit 1)))

        "delete"
        (if-let [agent-id (first params)]
          (remove-agent! agent-id)
          (do
            (println "❌ Usage: delete <agent-id>")
            (System/exit 1)))

        (do
          (println "\n📚 Pyjama Agent Registry")
          (println "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
          (println "\nUSAGE:")
          (println "  clj -M:pyjama registry <command> [args...]")
          (println "\nCOMANDS:")
          (println "  register <file>   Register an agent from an EDN file")
          (println "  list              List all registered agents")
          (println "  lookup <id>       Look up an agent by ID")
          (println "  remove <id>       Remove an agent from the registry")
          (println "  unregister <id>   (alias for remove)")
          (println "  delete <id>       (alias for remove)")
          (println "\nEXAMPLES:")
          (println "  clj -M:pyjama registry register examples/mermaid-diagram-generator.edn")
          (println "  clj -M:pyjama registry list")
          (println "  clj -M:pyjama registry lookup mermaid-diagram-generator")
          (println "  clj -M:pyjama registry remove mermaid-diagram-generator")
          (println "  clj -M:pyjama registry unregister mermaid-diagram-generator")
          (println "\nREGISTRY LOCATION:")
          (println (str "  " registry-dir))
          (println "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"))))

    (catch Exception e
      (println (str "\n❌ Error: " (.getMessage e)))
      (when (System/getProperty "debug")
        (.printStackTrace e))
      (System/exit 1))))
