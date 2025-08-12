(ns pyjama.cli.agent
 (:require
  [clojure.tools.cli :as cli]
  [clojure.java.io :as io]
  [pyjama.agent.core :as agent])
 (:gen-class))

(def cli-options
 [["-p" "--project-dir DIR" "Project directory"
   :default "."
   :validate [#(-> % io/as-file .exists) "Project dir does not exist"]]
  ["-r" "--prompt PROMPT" "Prompt to send"
   :default "document the agent.clj namespace and related DSL. The agent example you want to include is the edn code.edn"]
  ["-a" "--agents-edn PATH" "Path to agents.edn (set as System property)"
   :default "test-resources/agentic/code.edn"
   :validate [#(-> % io/as-file .exists) "agents.edn path does not exist"]]
  ["-i" "--id ID" "Agent/project id keyword or string"
   :default ":clj-project"]
  ["-h" "--help"]])

(defn- usage [summary]
 (str "Agentic CLI\n\n"
      "Usage: clj -M -m agentic.cli [opts]\n\n"
      "Options:\n" summary "\n"))

(defn- exit! [status msg]
 (binding [*out* (if (zero? status) *out* *err*)]
  (println msg))
 (System/exit status))

(defn run! [{:keys [project-dir prompt agents-edn id]}]
 ;; honor the library’s expectation on the agents file
 (System/setProperty "agents.edn" agents-edn)

 ;; allow id as keyword or string
 (let [id* (try
            (if (string? id)
             (keyword (clojure.string/replace id #"^:" ""))
             id)
            (catch Exception _
             (keyword (str id))))
       result (agent/call {:id          id*
                           :project-dir project-dir
                           :prompt      prompt})]
  ;; Do whatever you like with the result; for now, print it.
  (println (pr-str result))
  result))

(defn -main [& args]
 (let [{:keys [options arguments errors summary]}
       (cli/parse-opts args cli-options)]
  (cond
   (:help options) (exit! 0 (usage summary))
   (seq errors) (exit! 1 (str "Error:\n - " (clojure.string/join "\n - " errors)
                              "\n\n" (usage summary)))
   (seq arguments) (exit! 1 (str "Unexpected arguments: " (clojure.string/join " " arguments)
                                 "\n\n" (usage summary)))
   :else
   (try
    (run! options)
    (exit! 0 "Done.")
    (catch Throwable t
     (exit! 1 (str "Failure: " (.getMessage t))))))))