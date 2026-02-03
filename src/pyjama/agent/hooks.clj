(ns pyjama.agent.hooks
  "Post-execution hooks for agent tools.
  
  Provides a registry for tool hooks that execute automatically after tool execution.
  Useful for cross-cutting concerns like indexing, logging, metrics, etc.")

;; Registry of tool hooks
;; Format: {tool-keyword -> [hook-fn1 hook-fn2 ...]}
;; Each hook-fn receives: {:tool-name :write-file :args {...} :result {...} :ctx {...}}
(defonce ^:private hooks-registry (atom {}))

(defn register-hook!
  "Register a hook function to run after a specific tool executes.
  
  Args:
    tool-key - Keyword identifying the tool (e.g., :write-file)
    hook-fn  - Function that receives {:tool-name :args :result :ctx :params}
    
  Example:
    (register-hook! :write-file
      (fn [{:keys [result ctx]}]
        (when (:file result)
          (index-report {:report-file (:file result)
                        :project-dir (:project-dir ctx)
                        :agent-name (:id ctx)}))))"
  [tool-key hook-fn]
  (swap! hooks-registry update tool-key (fnil conj []) hook-fn))

(defn unregister-hook!
  "Remove a specific hook function for a tool."
  [tool-key hook-fn]
  (swap! hooks-registry update tool-key
         (fn [hooks]
           (vec (remove #(= % hook-fn) hooks)))))

(defn clear-hooks!
  "Clear all hooks for a specific tool, or all hooks if no tool specified."
  ([]
   (reset! hooks-registry {}))
  ([tool-key]
   (swap! hooks-registry dissoc tool-key)))

(defn get-hooks
  "Get all registered hooks for a tool."
  [tool-key]
  (get @hooks-registry tool-key []))

(defn run-hooks!
  "Execute all registered hooks for a tool.
  
  Args:
    tool-key - Keyword identifying the tool
    hook-data - Map containing {:tool-name :args :result :ctx :params}
    
  Returns: Vector of hook results (or errors)"
  [tool-key hook-data]
  (let [hooks (get-hooks tool-key)]
    (when (seq hooks)
      (mapv (fn [hook-fn]
              (try
                {:status :ok
                 :result (hook-fn hook-data)}
                (catch Exception e
                  {:status :error
                   :error (.getMessage e)
                   :exception e})))
            hooks))))

(comment
  ;; Example usage

  ;; Register a logging hook
  (register-hook! :write-file
                  (fn [{:keys [tool-name result]}]
                    (println "File written:" (:file result))))

  ;; Register an indexing hook
  (register-hook! :write-file
                  (fn [{:keys [result ctx]}]
                    (when (:file result)
                      (println "Indexing:" (:file result)))))

  ;; Clear all write-file hooks
  (clear-hooks! :write-file))
