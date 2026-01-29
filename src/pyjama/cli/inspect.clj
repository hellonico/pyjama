(ns pyjama.cli.inspect
  "CLI interface for pyjama system inspection"
  (:require
   [clojure.string :as str]
   [pyjama.inspect :as inspect]
   [cheshire.core :as json])
  (:gen-class))

(defn run-check-system
  "Run system check with optional verbose flag"
  [& args]
  (let [verbose? (some #{"--verbose" "-v"} args)
        json? (some #{"--json"} args)
        tools-file (or (some #(when (not (str/starts-with? % "-")) %) args)
                       "resources/agents/common-tools.edn")]

    (if json?
      ;; JSON output mode (for scripting/integration)
      (let [result (inspect/check-system :verbose? verbose? :tools-file tools-file)]
        (println (json/generate-string result {:pretty true})))

      ;; Human-readable output mode
      (inspect/check-system :verbose? verbose? :tools-file tools-file))))

(defn run-list-tools
  "List all tools in JSON format"
  [& args]
  (let [tools-file (or (first args) "resources/agents/common-tools.edn")
        result (inspect/list-tools tools-file)]
    (println (json/generate-string result {:pretty true}))))

(defn run-agent-stats
  "Get agent statistics in JSON format"
  []
  (let [result (inspect/agent-stats)]
    (println (json/generate-string result {:pretty true}))))

(defn print-help
  []
  (println "
Pyjama System Inspection Tool
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Usage: clj -M:inspect [command] [options]

Commands:
  check            Run full system check (agents + tools)
  tools            List all registered tools  
  stats            Show agent statistics

Options:
  -v, --verbose    Show detailed agent information
  --json           Output in JSON format (for scripting)

Examples:
  clj -M:inspect check                    # Basic system check
  clj -M:inspect check -v                 # Verbose system check
  clj -M:inspect check --json             # JSON output
  clj -M:inspect tools                    # List all tools
  clj -M:inspect stats                    # Agent statistics

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"))

(defn -main
  "Main entry point for inspect CLI"
  [& args]
  (try
    (if (empty? args)
      ;; Default to check command
      (run-check-system)

      (let [[command & params] args]
        (case command
          "check" (apply run-check-system params)
          "tools" (apply run-list-tools params)
          "stats" (run-agent-stats)
          "help" (print-help)
          "--help" (print-help)
          "-h" (print-help)

          ;; Unknown command - show help
          (do
            (println "❌ Unknown command:" command)
            (print-help)
            (System/exit 1)))))

    (catch Exception e
      (println "❌ Error:" (.getMessage e))
      (when (System/getProperty "debug")
        (.printStackTrace e))
      (System/exit 1)))

  (System/exit 0))
