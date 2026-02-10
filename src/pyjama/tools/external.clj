(ns pyjama.tools.external
  "Execute external Clojure project tools"
  (:require [clojure.java.shell :as shell]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]))

;; ----------------------------------------------------------------------------
;; Git Repository Caching
;; ----------------------------------------------------------------------------

(defn- cache-dir []
  "Get the tools cache directory"
  (let [home (System/getProperty "user.home")
        cache (io/file home ".pyjama" "tools-cache")]
    (.mkdirs cache)
    (.getAbsolutePath cache)))

(defn- git-url->cache-path [git-url]
  "Convert a Git URL to a cache directory path
   https://github.com/user/repo.git -> ~/.pyjama/tools-cache/github.com/user/repo"
  (let [;; Remove .git suffix
        url (str/replace git-url #"\.git$" "")
        ;; Extract host and path
        parts (re-find #"(?:https?://|git@)([^/:]+)[/:](.+)" url)]
    (when parts
      (let [host (nth parts 1)
            path (nth parts 2)
            ;; Convert git@host:path to host/path
            normalized-path (str/replace path #":" "/")]
        (io/file (cache-dir) host normalized-path)))))

(defn- clone-or-update-repo
  "Clone a Git repository to cache, or update if it exists.
   Returns the absolute path to the cloned repo."
  [git-url git-sha]
  (let [target-dir (git-url->cache-path git-url)]
    (if (.exists target-dir)
      ;; Already cached - optionally update
      (do
        (println "📦 Using cached tool from:" (.getAbsolutePath target-dir))
        (when git-sha
          (println "🔖 Checking out SHA:" git-sha)
          (shell/sh "git" "checkout" git-sha :dir (.getAbsolutePath target-dir)))
        (.getAbsolutePath target-dir))

      ;; Clone for the first time
      (do
        (println "📥 Cloning tool from:" git-url)
        (.mkdirs (.getParentFile target-dir))
        (let [result (shell/sh "git" "clone" git-url (.getAbsolutePath target-dir))]
          (if (zero? (:exit result))
            (do
              (when git-sha
                (println "🔖 Checking out SHA:" git-sha)
                (shell/sh "git" "checkout" git-sha :dir (.getAbsolutePath target-dir)))
              (println "✅ Tool cached successfully")
              (.getAbsolutePath target-dir))
            (throw (ex-info "Failed to clone Git repository"
                            {:git-url git-url
                             :exit-code (:exit result)
                             :stderr (:err result)}))))))))

(defn- resolve-project-path
  "Resolve the project path - either local or Git URL"
  [{:keys [project-path git-url git-sha subdir]}]
  (let [base-path (if git-url
                    (clone-or-update-repo git-url git-sha)
                    (str/replace project-path #"^~" (System/getProperty "user.home")))]
    (if subdir
      (.getAbsolutePath (io/file base-path subdir))
      base-path)))

;; ----------------------------------------------------------------------------
;; External Tool Execution
;; ----------------------------------------------------------------------------

(defn execute-clojure-tool
  "Execute a function from an external Clojure project.
   
   Args:
   - project-path: Local path to the Clojure project (with deps.edn)
   - git-url: Git repository URL (alternative to project-path)
   - git-sha: Optional Git commit SHA/tag to pin to specific version
   - subdir: Optional subdirectory within the repo
   - namespace: Namespace containing the function
   - function: Function name to execute
   - params: Parameters to pass to the function
   
   The external tool must accept EDN on stdin with format:
   {:function \"function-name\" :params {...}}
   
   And return EDN on stdout with format:
   {:status :ok :result ...} or {:status :error :message ...}
   
   Examples:
   
   Local tool:
   {:project-path \"~/tools/greeter-tool\"
    :namespace \"greeter-tool.core\"
    :function \"greet\"
    :params {:name \"Alice\"}}
   
   Git-based tool:
   {:git-url \"https://github.com/user/pyjama-tools.git\"
    :subdir \"greeter-tool\"
    :namespace \"greeter-tool.core\"
    :function \"greet\"
    :params {:name \"Alice\"}}
   
   Pinned version:
   {:git-url \"https://github.com/user/pyjama-tools.git\"
    :git-sha \"v1.2.3\"
    :subdir \"greeter-tool\"
    :namespace \"greeter-tool.core\"
    :function \"greet\"
    :params {:name \"Alice\"}}"
  [{:keys [namespace function params] :as args}]

  (let [;; Resolve the project path (local or Git)
        expanded-path (resolve-project-path args)

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
