(ns pyjama.tools.external
  "Execute external Clojure project tools"
  (:require [clojure.java.shell :as shell]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(defn execute-clojure-tool
  "Execute a function from an external Clojure project.
   
   Args:
   - project-path: Path to the Clojure project (with deps.edn)
   - namespace: Namespace containing the function
   - function: Function name to execute
   - params: Parameters to pass to the function
   
   The external tool must accept EDN on stdin with format:
   {:function \"function-name\" :params {...}}
   
   And return EDN on stdout with format:
   {:status :ok :result ...} or {:status :error :message ...}"
  [{:keys [project-path namespace function params message ctx] :as args}]

  (let [;; Expand home directory
        expanded-path (str/replace project-path #"^~" (System/getProperty "user.home"))

        ;; Build the input EDN
        input-data {:function function
                    :params (or params {})}
        input-edn (pr-str input-data)

        ;; Build the clj command
        ;; We use -M -m to run the namespace's -main function
        result (shell/sh "clj" "-M" "-m" namespace
                         :dir expanded-path
                         :in input-edn)]

    (if (zero? (:exit result))
      (try
        ;; Parse the EDN output
        (edn/read-string (:out result))
        (catch Exception e
          {:status :error
           :message (str "Failed to parse tool output: " (.getMessage e))
           :raw-output (:out result)
           :stderr (:err result)}))

      ;; Command failed
      {:status :error
       :message "Tool execution failed"
       :exit-code (:exit result)
       :stdout (:out result)
       :stderr (:err result)})))

(defn register-external-tool
  "Helper to create a tool definition for an external Clojure project.
   
   Returns a tool spec that can be used in agent definitions."
  [project-path namespace]
  {:fn 'pyjama.tools.external/execute-clojure-tool
   :args {:project-path project-path
          :namespace namespace}})
