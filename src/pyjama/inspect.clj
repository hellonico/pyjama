(ns pyjama.inspect
  "System inspection and monitoring for Pyjama agent ecosystem.
   Provides utilities to analyze agents, tools, and system health."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [pyjama.core :as pyjama]
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

(def ^:private emojis
  "Unicode emojis for visual indicators"
  {:check  "✓"
   :cross  "✗"
   :info   "ℹ"
   :agent  "🤖"
   :tool   "🔧"
   :input  "📥"
   :output "📤"
   :step   "⚙️"})

(defn- colorize
  "Apply color to text"
  [color text]
  (str (get colors color "") text (get colors :reset "")))

(defn- emoji
  "Get emoji by key"
  [key]
  (get emojis key ""))

;; =============================================================================
;; Tool Analysis
;; =============================================================================

(defn- load-tools-config
  "Load tools from common-tools.edn file"
  [tools-file]
  (when (.exists (io/file tools-file))
    (try
      (edn/read-string (slurp tools-file))
      (catch Exception e
        (println (colorize :red (str "Error loading tools config: " (.getMessage e))))
        {}))))

(defn- extract-tool-names
  "Extract tool names from tools config map"
  [tools-config]
  (when tools-config
    ;; Tools are nested under :tools key in the EDN structure
    (let [tools-map (:tools tools-config tools-config)]
      (->> tools-map
           keys
           (map name)
           sort))))

(defn- categorize-tools
  "Group tools into logical categories based on naming patterns"
  [tool-names]
  (let [patterns {:git       #"(?i)(git|pr|branch)"
                  :analysis  #"(?i)(discover|codebase|analyze|extract|format)"
                  :retrieval #"(?i)(pick|retrieve|search|classify)"
                  :io        #"(?i)(template|write|read|notify|passthrough)"}]

    (->> tool-names
         (group-by (fn [tool]
                     (or (some (fn [[category pattern]]
                                 (when (re-find pattern tool)
                                   category))
                               patterns)
                         :other)))
         (into (sorted-map)))))

(defn- count-tools-by-category
  "Count tools in each category"
  [categorized-tools]
  (->> categorized-tools
       (map (fn [[category tools]] [category (count tools)]))
       (into {})))

;; =============================================================================
;; Agent Analysis
;; =============================================================================

(defn- format-input-info
  "Format input parameter information for display"
  [input-key spec]
  (let [input-type (get spec :type "any")
        required? (get spec :required true)
        description (get spec :description "No description")
        default-val (get spec :default nil)
        req-icon (if required?
                   (colorize :red "●")
                   (colorize :gray "○"))
        req-text (if required?
                   (colorize :red "required")
                   (colorize :gray "optional"))]
    {:icon req-icon
     :key (colorize :cyan (name input-key))
     :type (colorize :yellow input-type)
     :req-text req-text
     :description description
     :default default-val}))

(defn- format-step-info
  "Format step information for display"
  [step-key step-spec]
  (cond
    (map? step-spec)
    (let [tool (:tool step-spec)
          prompt (:prompt step-spec)
          next-step (get step-spec :next "(end)")]
      {:key (colorize :cyan (name step-key))
       :tool (cond
               tool (colorize :yellow tool)
               prompt (colorize :purple "(LLM)")
               :else "")
       :next next-step})

    (string? step-spec)
    {:key (colorize :cyan (name step-key))
     :tool (colorize :gray "(inline)")
     :next ""}

    :else
    {:key (colorize :cyan (name step-key))
     :tool (colorize :gray "(unknown)")
     :next ""}))

;; =============================================================================
;; Display Functions
;; =============================================================================

(defn- print-banner
  "Print system check banner"
  []
  (println)
  (println (colorize :purple "╔════════════════════════════════════════════════════════════════╗"))
  (println (colorize :purple "║") (colorize :white "                PYJAMA - SYSTEM CHECK                      ") (colorize :purple "║"))
  (println (colorize :purple "╚════════════════════════════════════════════════════════════════╝"))
  (println))

(defn- print-section-header
  "Print a section header"
  [title]
  (println)
  (println (colorize :white "══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════"))
  (println (colorize :white title))
  (println (colorize :white "══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════"))
  (println))

(defn- print-agents-overview
  "Print overview table of all agents"
  [agents]
  (print-section-header (str (emoji :agent) " AGENTS OVERVIEW"))
  (printf "%s%-45s%s %s%-75s%s\n"
          (get colors :cyan "") "AGENT ID" (get colors :reset "")
          (get colors :yellow "") "DESCRIPTION" (get colors :reset ""))
  (println (colorize :gray "──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────"))

  (doseq [agent agents]
    (let [id (name (:id agent))
          desc (:description agent)
          truncated-desc (if (> (count desc) 73)
                           (str (subs desc 0 70) "...")
                           desc)]
      (printf "%s%-45s%s %s%-75s%s\n"
              (get colors :cyan "") id (get colors :reset "")
              (get colors :gray "") truncated-desc (get colors :reset ""))))
  (println))

(defn- print-agent-details
  "Print detailed information about a single agent"
  [agent]
  (println (colorize :purple "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"))
  (printf "%s┃%s Agent: %s%-70s%s┃%s\n"
          (get colors :purple "") (get colors :white "")
          (get colors :cyan "") (name (:id agent))
          (get colors :purple "") (get colors :reset ""))
  (println (colorize :purple "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"))

  ;; Description
  (println "  " (colorize :gray "Description:") (:description agent))
  (println)

  ;; Inputs
  (when-let [spec (:spec agent)]
    (when-let [inputs (:inputs spec)]
      (let [input-count (count inputs)]
        (println "  " (colorize :green (str (emoji :input) " Inputs (" input-count "):")))
        (doseq [[input-key input-spec] inputs]
          (let [info (format-input-info input-key input-spec)]
            (printf "    %s %s %s(%s, %s)%s\n"
                    (:icon info) (:key info) (get colors :gray "")
                    (:type info) (:req-text info) (get colors :reset ""))
            (println "      " (colorize :gray "├─") (:description info))
            (when-let [default-val (:default info)]
              (println "      " (colorize :gray "└─ default:")
                       (colorize :white (str default-val))))))
        (println)))

    ;; Steps
    (when-let [steps (:steps spec)]
      (let [step-count (count steps)
            start-step (get spec :start "unknown")]
        (println "  " (colorize :blue (str (emoji :step) " Workflow (" step-count " steps):")))
        (println "    " (colorize :gray "Start:") (colorize :green start-step))
        (println)

        (doseq [[step-key step-spec] steps]
          (let [info (format-step-info step-key step-spec)
                prefix (colorize :gray "├─")]
            (if (not-empty (:tool info))
              (printf "    %s %s %s→%s %s %s→%s %s\n"
                      prefix (:key info) (get colors :gray "")
                      (get colors :reset "") (:tool info)
                      (get colors :gray "") (get colors :reset "")
                      (:next info))
              (printf "    %s %s %s→%s %s\n"
                      prefix (:key info) (get colors :gray "")
                      (get colors :reset "") (:next info)))))
        (println)))

    ;; Metadata
    (let [max-steps (get spec :max-steps 20)
          agent-type (:type agent "unknown")]
      (println "  " (colorize :gray "Type:") (colorize :white agent-type)
               " " (colorize :gray "Max Steps:") (colorize :white (str max-steps))))
    (println)))

(defn- print-tools-section
  "Print tools categorized by type"
  [categorized-tools tool-count]
  (print-section-header (str (emoji :tool) " REGISTERED TOOLS"))

  (println (colorize :green (str (emoji :check) " Found " tool-count " registered tools")))
  (println)

  (let [category-names {:git "Git Tools"
                        :analysis "Code Analysis Tools"
                        :retrieval "Retrieval Tools"
                        :io "Template & I/O Tools"
                        :other "Other Tools"}]

    (doseq [[category tools] categorized-tools]
      (when (seq tools)
        (let [category-name (get category-names category (name category))
              count (count tools)]
          (println (colorize :cyan (str category-name " (" count "):")))
          (doseq [tool tools]
            (println "  " (colorize :gray "•") tool))
          (println))))))

(defn- print-summary
  "Print summary statistics"
  [agent-count tool-count]
  (print-section-header (str (emoji :info) " SUMMARY"))

  (printf "  %s%-30s%s %s%s%s\n"
          (get colors :cyan "") "Total Agents:" (get colors :reset "")
          (get colors :white "") agent-count (get colors :reset ""))
  (printf "  %s%-30s%s %s%s%s\n"
          (get colors :cyan "") "Total Tools:" (get colors :reset "")
          (get colors :white "") tool-count (get colors :reset ""))
  (println)
  (println (colorize :green (str (emoji :check) " System check complete!"))))

;; =============================================================================
;; Public API
;; =============================================================================

(defn check-system
  "Perform system check of agents and tools.
   
   Options:
   - :verbose? - Show detailed agent information (default: false)
   - :tools-file - Path to common-tools.edn (default: resources/agents/common-tools.edn)"
  [& {:keys [verbose? tools-file]
      :or {verbose? false
           tools-file "resources/agents/common-tools.edn"}}]

  (print-banner)

  ;; Load and display agents
  (println (colorize :cyan (str (emoji :agent) " Loading agents...")))
  (let [;; Get local agents
        local-agents (pyjama/list-agents)

        ;; Get registry agents
        registry-agents (try
                          (registry/list-registered-agents)
                          (catch Exception _ []))

        ;; Tag registry agents
        registry-agents-tagged (map #(assoc % :description (str (:description %) " [registry]"))
                                    registry-agents)

        ;; Combine all agents
        all-agents (concat local-agents registry-agents-tagged)
        agent-count (count all-agents)]

    (if (zero? agent-count)
      (do
        (println (colorize :red (str (emoji :cross) " No agents found")))
        {:agents [] :tools [] :counts {:agents 0 :tools 0}})

      (do
        (println (colorize :green (str (emoji :check) " Found " agent-count " agents")))
        (println (str "  " (colorize :cyan "Local:") " " (count local-agents)
                      "  " (colorize :purple "Registry:") " " (count registry-agents-tagged)))
        (println)

        ;; Print agents overview
        (print-agents-overview all-agents)

        ;; Print detailed info if verbose
        (when verbose?
          (print-section-header (str (emoji :agent) " DETAILED AGENT INFORMATION"))
          (doseq [agent all-agents]
            (print-agent-details agent)))

        ;; Load and display tools
        (let [tools-config (load-tools-config tools-file)
              tool-names (extract-tool-names tools-config)
              tool-count (count tool-names)
              categorized-tools (categorize-tools tool-names)]

          (when (pos? tool-count)
            (print-tools-section categorized-tools tool-count))

          ;; Print summary
          (print-summary agent-count tool-count)

          ;; Tip for verbose mode
          (when-not verbose?
            (println)
            (println (colorize :gray
                               (str "💡 Tip: Use :verbose? true for detailed agent information"))))

          (println)

          ;; Return data for programmatic use
          {:agents all-agents
           :tools (map (fn [[category tools]] {:category category :tools tools})
                       categorized-tools)
           :counts {:agents agent-count
                    :tools tool-count}})))))

(defn list-tools
  "List all tools from a tools configuration file.
   Returns a map with categorized tools."
  [tools-file]
  (let [tools-config (load-tools-config tools-file)
        tool-names (extract-tool-names tools-config)
        categorized (categorize-tools tool-names)]
    {:total (count tool-names)
     :categories categorized
     :counts (count-tools-by-category categorized)}))

(defn agent-stats
  "Get statistics about the agent registry.
   Returns a map with counts by type and other metrics."
  []
  (let [local-agents (pyjama/list-agents)
        registry-agents (try
                          (registry/list-registered-agents)
                          (catch Exception _ []))
        all-agents (concat local-agents registry-agents)
        by-type (group-by :type all-agents)]
    {:total (count all-agents)
     :local-count (count local-agents)
     :registry-count (count registry-agents)
     :by-type (into {} (map (fn [[type agents]] [type (count agents)]) by-type))
     :agents (map #(select-keys % [:id :type :description]) all-agents)}))