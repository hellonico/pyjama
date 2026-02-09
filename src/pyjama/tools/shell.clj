(ns pyjama.tools.shell
  "Shell command execution tool for pyjama agents."
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn- expand-env-vars
  "Expand environment variables in a string.
   Supports both $VAR and ${VAR} syntax.
   
   Examples:
     $HOME -> /Users/username
     ${USER}_backup -> username_backup"
  [s]
  (if-not (string? s)
    s
    (let [env (System/getenv)]
      (-> s
          ;; First expand ${VAR} syntax
          (str/replace #"\$\{([^}]+)\}"
                       (fn [[_ var-name]]
                         (or (get env var-name) "")))
          ;; Then expand $VAR syntax (word boundaries)
          (str/replace #"\$([A-Za-z_][A-Za-z0-9_]*)"
                       (fn [[_ var-name]]
                         (or (get env var-name) "")))))))

(defn- glob-expand
  "Expand glob patterns in a string using the shell's built-in globbing.
   
   Supports standard glob patterns:
     * - matches any characters within a directory
     ** - matches across directories (in shells that support it)
     ? - matches a single character
     [abc] - matches any character in the set
   
   Examples:
     '*.clj' -> ['file1.clj' 'file2.clj']
     'src/**/*.clj' -> ['src/core.clj' 'src/util/helper.clj']
     
   Returns the input unchanged if no glob patterns are found or no matches exist."
  [pattern]
  (if-not (string? pattern)
    pattern
    (if (or (str/includes? pattern "*")
            (str/includes? pattern "?")
            (str/includes? pattern "["))
      (try
        ;; Use shell to expand the glob
        (let [result (shell/sh "bash" "-c" (str "echo " pattern))
              expanded (str/trim (:out result))]
          (if (and (zero? (:exit result))
                   (seq expanded)
                   (not= expanded pattern)) ; Check if it actually expanded
            (str/split expanded #"\s+")
            [pattern])) ; Return original if no expansion
        (catch Exception _e
          [pattern])) ; Return original on error
      [pattern])))

(defn execute-command
  "Execute a shell command and return the result.
   
   Args:
     :command - The command to execute (string or vector of strings)
     :dir - Optional working directory (default: current directory)
     :env - Optional environment variables map
     :timeout - Optional timeout in milliseconds (default: 60000 = 1 minute)
     :expand-env? - Expand environment variables like $HOME (default: true)
     :expand-glob? - Expand glob patterns like *.clj (default: true)
   
   Returns:
     {:status :ok/:error
      :exit - Exit code
      :out - Standard output
      :err - Standard error
      :command - The executed command}"
  [{:keys [command dir env timeout expand-env? expand-glob?]
    :or {timeout 60000 expand-env? true expand-glob? true}
    :as _args}]
  (try
    (let [;; Process command: expand env vars and globs
          expanded-cmd (cond
                         ;; String command - expand env vars then split
                         (string? command)
                         (let [with-env (if expand-env?
                                          (expand-env-vars command)
                                          command)]
                           (str/split with-env #"\s+"))

                         ;; Vector command - expand each element
                         (sequential? command)
                         (mapv (fn [arg]
                                 (let [with-env (if expand-env?
                                                  (expand-env-vars (str arg))
                                                  (str arg))]
                                   with-env))
                               command)

                         :else
                         [command])

          ;; Apply globbing if requested (only to the first non-command arguments)
          final-cmd (if expand-glob?
                      (let [[cmd & args] expanded-cmd
                            expanded-args (mapcat (fn [arg]
                                                    (let [result (glob-expand arg)]
                                                      (if (sequential? result)
                                                        result
                                                        [result])))
                                                  args)]
                        (vec (cons cmd expanded-args)))
                      expanded-cmd)

          opts (cond-> {}
                 dir (assoc :dir dir)
                 env (assoc :env env))

          ;; Execute with timeout
          result (future
                   (apply shell/sh (concat final-cmd (flatten (seq opts)))))
          output (deref result timeout ::timeout)]

      (if (= output ::timeout)
        {:status :error
         :message (str "Command timed out after " timeout "ms")
         :command (if (string? command) command (str/join " " command))
         :timeout timeout}
        {:status (if (zero? (:exit output)) :ok :error)
         :exit (:exit output)
         :out (:out output)
         :err (:err output)
         :command (str/join " " final-cmd)
         :original-command (if (string? command) command (str/join " " command))
         :text (str "Command: " (str/join " " final-cmd) "\n"
                    "Exit code: " (:exit output) "\n"
                    (when (seq (:out output))
                      (str "Output:\n" (:out output)))
                    (when (seq (:err output))
                      (str "Error:\n" (:err output))))}))
    (catch Exception e
      {:status :error
       :message (.getMessage e)
       :command (if (string? command) command (str/join " " command))
       :exception (str e)})))

(defn run-script
  "Execute a shell script from a string or file.
   
   Args:
     :script - Shell script content (string)
     :file - Optional path to script file (overrides :script)
     :shell - Shell to use (default: /bin/bash)
     :dir - Optional working directory
     :env - Optional environment variables map
     :timeout - Optional timeout in milliseconds
   
   Returns:
     Same format as execute-command"
  [{:keys [script file shell _dir _env _timeout]
    :or {shell "/bin/bash"}
    :as args}]
  (let [script-content (if file
                         (slurp file)
                         script)]
    (execute-command
     (assoc args
            :command [shell "-c" script-content]))))
