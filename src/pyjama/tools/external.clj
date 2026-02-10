(ns pyjama.tools.external
  "External Clojure tool execution via Git repositories or local paths"
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.edn :as edn]))

(defn- cache-dir []
  (io/file (System/getProperty "user.home") ".pyjama" "tools-cache"))

(defn- git-url->cache-path [git-url]
  (let [url (str/replace git-url #"\.git$" "")
        parts (re-find #"(?:https?://|git@)([^/:]+)[/:](.+)" url)]
    (when parts
      (let [host (nth parts 1)
            path (nth parts 2)
            normalized-path (str/replace path #":" "/")]
        (io/file (cache-dir) host normalized-path)))))

(defn- clone-or-update-repo [git-url git-sha git-pull]
  (let [target-dir (git-url->cache-path git-url)]
    (if (.exists target-dir)
      (do
        (println "📦 Using cached tool from:" (.getAbsolutePath target-dir))
        (when git-sha
          (println "🔖 Checking out:" git-sha)
          (shell/sh "git" "checkout" git-sha :dir (.getAbsolutePath target-dir)))
        (when git-pull
          (println "�� Pulling latest changes...")
          (let [result (shell/sh "git" "pull" :dir (.getAbsolutePath target-dir))]
            (if (zero? (:exit result))
              (println "✅ Updated to latest version")
              (println "⚠️  Warning: git pull failed:" (:err result)))))
        (.getAbsolutePath target-dir))
      (do
        (println "📥 Cloning tool from:" git-url)
        (.mkdirs (.getParentFile target-dir))
        (let [result (shell/sh "git" "clone" git-url (.getAbsolutePath target-dir))]
          (if (zero? (:exit result))
            (do
              (when git-sha
                (println "🔖 Checking out:" git-sha)
                (shell/sh "git" "checkout" git-sha :dir (.getAbsolutePath target-dir)))
              (println "✅ Tool cached successfully")
              (.getAbsolutePath target-dir))
            (throw (ex-info "Failed to clone Git repository"
                            {:git-url git-url
                             :exit-code (:exit result)
                             :stderr (:err result)}))))))))

(defn- resolve-project-path [{:keys [project-path git-url git-sha git-pull subdir]}]
  (let [base-path (if git-url
                    (clone-or-update-repo git-url git-sha git-pull)
                    (str/replace project-path #"^~" (System/getProperty "user.home")))]
    (if subdir
      (.getAbsolutePath (io/file base-path subdir))
      base-path)))

(defn- find-clj-command []
  (let [paths ["/opt/homebrew/bin/clj"
               "/usr/local/bin/clj"
               "/usr/bin/clj"
               "clj"]]
    (or (first (filter #(and (not= % "clj")
                             (.exists (io/file %)))
                       paths))
        "clj")))

(defn execute-clojure-tool [{:keys [namespace function params] :as args}]
  (let [expanded-path (resolve-project-path args)
        input-data {:function function :params (or params {})}
        input-edn (pr-str input-data)
        clj-cmd (find-clj-command)
        cmd-str (str "cd " expanded-path " && " clj-cmd " -M -m " namespace)
        result (shell/sh "bash" "-c" cmd-str :in input-edn)]
    (if (zero? (:exit result))
      (try
        (edn/read-string (:out result))
        (catch Exception e
          {:status :error
           :message (str "Failed to parse tool output: " (.getMessage e))
           :raw-output (:out result)
           :stderr (:err result)}))
      {:status :error
       :message "Tool execution failed"
       :exit-code (:exit result)
       :stdout (:out result)
       :stderr (:err result)})))
