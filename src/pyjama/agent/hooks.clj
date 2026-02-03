(ns pyjama.agent.hooks
  "Hook system for agent tools with pre and post-execution phases.
  
  Provides a registry for tool hooks that execute automatically:
  - Pre-execution hooks: Run before tool execution (validation, setup, etc.)
  - Post-execution hooks: Run after tool execution (indexing, logging, etc.)
  
  Useful for cross-cutting concerns like indexing, logging, metrics, validation, etc.")

;; Registry of tool hooks
;; Format: {tool-keyword -> {:pre [hook-fn1 ...] :post [hook-fn2 ...]}}
;; Each hook-fn receives: {:tool-name :args :result :ctx :params}
(defonce ^:private hooks-registry (atom {}))

(defn register-hook!
  "Register a hook function to run after a specific tool executes (post-execution).
  
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
  (swap! hooks-registry update-in [tool-key :post] (fnil conj []) hook-fn))

(defn register-pre-hook!
  "Register a hook function to run before a specific tool executes.
  
  Pre-hooks can:
  - Validate inputs
  - Set up resources
  - Modify arguments (by returning modified hook-data)
  - Cancel execution (by throwing an exception)
  
  Args:
    tool-key - Keyword identifying the tool
    hook-fn  - Function that receives {:tool-name :args :ctx :params}
               Can return modified hook-data or nil
  
  Example:
    (register-pre-hook! :write-file
      (fn [{:keys [args]}]
        (when-not (:path args)
          (throw (ex-info \"Missing :path argument\" {})))))"
  [tool-key hook-fn]
  (swap! hooks-registry update-in [tool-key :pre] (fnil conj []) hook-fn))

(defn unregister-hook!
  "Remove a specific post-execution hook function for a tool."
  [tool-key hook-fn]
  (swap! hooks-registry update-in [tool-key :post]
         (fn [hooks]
           (vec (remove #(= % hook-fn) hooks)))))

(defn unregister-pre-hook!
  "Remove a specific pre-execution hook function for a tool."
  [tool-key hook-fn]
  (swap! hooks-registry update-in [tool-key :pre]
         (fn [hooks]
           (vec (remove #(= % hook-fn) hooks)))))

(defn clear-hooks!
  "Clear all hooks for a specific tool, or all hooks if no tool specified."
  ([]
   (reset! hooks-registry {}))
  ([tool-key]
   (swap! hooks-registry dissoc tool-key)))

(defn get-hooks
  "Get all registered post-execution hooks for a tool."
  [tool-key]
  (get-in @hooks-registry [tool-key :post] []))

(defn get-pre-hooks
  "Get all registered pre-execution hooks for a tool."
  [tool-key]
  (get-in @hooks-registry [tool-key :pre] []))

(defn run-pre-hooks!
  "Execute all registered pre-execution hooks for a tool.
  
  Pre-hooks run before tool execution and can:
  - Validate inputs
  - Modify arguments
  - Cancel execution (by throwing)
  
  Args:
    tool-key - Keyword identifying the tool
    hook-data - Map containing {:tool-name :args :ctx :params}
    
  Returns: Modified hook-data (or original if no modifications)"
  [tool-key hook-data]
  (let [hooks (get-pre-hooks tool-key)]
    (if (seq hooks)
      (reduce (fn [data hook-fn]
                (try
                  (or (hook-fn data) data)
                  (catch Exception e
                    (binding [*out* *err*]
                      (println (str "⚠️  Pre-hook failed for " tool-key ": " (.getMessage e))))
                    (throw e))))
              hook-data
              hooks)
      hook-data)))

(defn run-hooks!
  "Execute all registered post-execution hooks for a tool.
  
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

  ;; Register a post-execution logging hook
  (register-hook! :write-file
                  (fn [{:keys [tool-name result]}]
                    (println "File written:" (:file result))))

  ;; Register a pre-execution validation hook
  (register-pre-hook! :write-file
                      (fn [{:keys [args]}]
                        (when-not (:path args)
                          (throw (ex-info "Missing :path argument" {})))))

  ;; Register a pre-execution hook that modifies args
  (register-pre-hook! :write-file
                      (fn [hook-data]
                        (update-in hook-data [:args :message]
                                   #(str "<!-- Auto-generated -->\n" %))))

  ;; Clear all write-file hooks
  (clear-hooks! :write-file))
