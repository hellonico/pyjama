(ns pyjama.cli.call
 (:require [clojure.java.io :as io]
           [pyjama.core :as pyjama]
           [clojure.tools.cli])
 (:import [java.io File]
          [java.lang System])
 (:gen-class))

(defn find-agents-file []
 (let [candidates [(io/file "resources/agents.edn")
                   (io/file "agents.edn")
                   (io/file (System/getProperty "user.home") ".pyjama" "agents.edn")]]
  (some #(when (.exists ^File %) (.getAbsolutePath ^File %)) candidates)))

(defn debug-enabled? []
 (or (= "true" (System/getenv "DEBUG"))
     (= "true" (System/getProperty "debug"))))

(defn set-agents-property! []
 (if (System/getProperty "agents.edn")
  (when (debug-enabled?)
   (println "System property 'agents.edn' is already set, skipping."))
  (if-let [path (find-agents-file)]
   (do
    (System/setProperty "agents.edn" path)
    (when (debug-enabled?)
     (println "Set system property 'agents.edn' to:" path)))
   (when (debug-enabled?)
    (println "agents.edn not found in any known location.")))))


;; === CLI options ===
(def cli-options
 [["-i" "--id ID" "Agent ID"
   :required true]
  ["-p" "--prompt PROMPT" "Prompt"
   :required true]
  ["-s" "--streaming" "Enable streaming?"
   :default false]
  ["-h" "--help"]])

;; === Main ===
(defn -main [& args]
 (let [{:keys [options errors summary]} (clojure.tools.cli/parse-opts args cli-options)]
  (cond
   (:help options)
   (do
    (println "Usage: program [options]")
    (println summary))

   errors
   (doseq [err errors]
    (println "Error:" err))

   :else
   (do
    (set-agents-property!)
    (let [{:keys [id prompt streaming]} options
          response (pyjama/call {:id        (keyword id)
                                 :prompt    prompt
                                 :stream streaming})]
     (when (not streaming)
      (println response)))))))