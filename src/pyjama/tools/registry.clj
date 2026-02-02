(ns pyjama.tools.registry
  "Tool registry system for automatic tool discovery and registration.
  
  This namespace provides utilities to:
  1. Auto-discover tools from namespaces following the `register-tools!` convention
  2. Register tool namespaces globally for easy agent access
  3. Support wildcard imports in agent EDN files
  
  Usage:
  
  In your tool namespace (e.g., plane-client.pyjama.tools):
  ```clojure
  (defn register-tools! []
    {:create-issue {:fn create-issue-fn :description \"...\"}
     :list-items {:fn list-items-fn :description \"...\"}})
  ```
  
  Then register the namespace:
  ```clojure
  (require '[pyjama.tools.registry :as registry])
  (registry/register-namespace! 'plane-client.pyjama.tools)
  ```
  
  Or in agent EDN, use wildcard import:
  ```edn
  :tools {:* plane-client.pyjama.tools}
  ```
  "
  (:require [clojure.string :as str]))

;; Registry of tool namespaces that have been registered.
;; Map of namespace-symbol -> tool-map
(defonce ^:private tool-namespaces (atom {}))

(defn register-namespace!
  "Register a namespace that provides tools via a `register-tools!` function.
  
  The namespace must have a public `register-tools!` function that returns
  a map of tool-keyword -> {:fn ... :description ...}
  
  Example:
    (register-namespace! 'plane-client.pyjama.tools)
  
  Returns the registered tool map."
  [ns-sym]
  (try
    (require ns-sym)
    (if-let [register-fn (ns-resolve ns-sym 'register-tools!)]
      (let [tools (register-fn)]
        (swap! tool-namespaces assoc ns-sym tools)
        (println (str "✓ Registered " (count tools) " tools from " ns-sym))
        tools)
      (throw (ex-info (str "Namespace " ns-sym " does not have a register-tools! function")
                      {:namespace ns-sym})))
    (catch Exception e
      (binding [*out* *err*]
        (println (str "⚠️  Failed to register tools from " ns-sym ": " (.getMessage e))))
      (throw e))))

(defn unregister-namespace!
  "Remove a namespace from the tool registry."
  [ns-sym]
  (swap! tool-namespaces dissoc ns-sym))

(defn get-namespace-tools
  "Get all tools registered from a specific namespace."
  [ns-sym]
  (get @tool-namespaces ns-sym))

(defn list-registered-namespaces
  "List all registered tool namespaces."
  []
  (keys @tool-namespaces))

(defn all-tools
  "Get a merged map of all tools from all registered namespaces.
   Later registrations override earlier ones if there are conflicts."
  []
  (apply merge (vals @tool-namespaces)))

(defn normalize-tool-def
  "Normalize a tool definition to the standard {:fn ... :description ...} format.
  
  Supports multiple input formats:
  1. Full tool def: {:fn my-ns/my-fn :description \"...\"}
  2. Symbol reference: 'my-ns/my-fn
  3. Direct function: #'my-ns/my-fn or (fn [obs] ...)
  
  For simple function references, automatically wraps them with a default description."
  [tool-def]
  (cond
    ;; Already a proper tool definition
    (and (map? tool-def) (:fn tool-def))
    tool-def

    ;; Symbol reference to a function
    (symbol? tool-def)
    {:fn tool-def
     :description (str "Tool: " tool-def)}

    ;; Direct function value (var or fn)
    (or (var? tool-def) (ifn? tool-def))
    (let [fn-name (if (var? tool-def)
                    (str (:ns (meta tool-def)) "/" (:name (meta tool-def)))
                    "anonymous-fn")]
      {:fn tool-def
       :description (str "Tool: " fn-name)})

    ;; Map without :fn key - assume it's {:fn value}
    (map? tool-def)
    (if (= 1 (count tool-def))
      (let [[k v] (first tool-def)]
        {:fn v :description (str "Tool: " (name k))})
      tool-def)

    ;; Unknown format - return as-is and let validation catch it
    :else tool-def))

(defn normalize-tools-map
  "Normalize all tool definitions in a tools map.
  
  Converts simple function references to proper tool definitions:
  {:my-tool 'my-ns/my-fn} => {:my-tool {:fn 'my-ns/my-fn :description \"...\"}}
  
  This allows for more concise tool definitions in agent EDN files."
  [tools-map]
  (into {}
        (map (fn [[k v]]
               [k (normalize-tool-def v)])
             tools-map)))

(defn expand-wildcard-tools
  "Expand wildcard tool imports in agent tool definitions.
  
  Supports the following patterns:
  
  1. Import all tools from a namespace:
     {:* 'plane-client.pyjama.tools}
  
  2. Import all tools with a prefix:
     {:plane/* 'plane-client.pyjama.tools}
     ; Creates :plane/create-issue, :plane/list-items, etc.
  
  3. Mix with explicit tools:
     {:* 'plane-client.pyjama.tools
      :custom-tool {:fn my-ns/my-fn}}
  
  Returns an expanded tool map with all wildcards resolved."
  [tools-map]
  (if (nil? tools-map)
    {}
    (let [;; Helper to check if a keyword is a wildcard
          wildcard? (fn [k]
                      (or (= k :*)
                          ;; Check for :prefix/* pattern
                          (and (keyword? k)
                               (nil? (namespace k))
                               (str/ends-with? (name k) "/*"))
                          ;; Check for namespaced wildcard like :mock/*
                          (and (keyword? k)
                               (some? (namespace k))
                               (= "*" (name k)))))

          ;; Separate wildcard entries from explicit tools
          {wildcards true explicit false}
          (group-by (fn [[k _]] (wildcard? k)) tools-map)

          ;; Process wildcards
          expanded-wildcards
          (reduce
           (fn [acc [k v]]
             (cond
               ;; Simple wildcard: {:* 'namespace}
               (= k :*)
               (let [ns-sym (if (symbol? v) v (:namespace v))
                     tools (or (get-namespace-tools ns-sym)
                               (register-namespace! ns-sym))]
                 (merge acc tools))

               ;; Namespaced wildcard: {:mock/* 'namespace} (EDN reads as #:mock{:* ...})
               (and (keyword? k) (some? (namespace k)) (= "*" (name k)))
               (let [prefix (namespace k)
                     ns-sym (if (symbol? v) v (:namespace v))
                     tools (or (get-namespace-tools ns-sym)
                               (register-namespace! ns-sym))
                     prefixed-tools (into {}
                                          (map (fn [[tool-k tool-def]]
                                                 [(keyword prefix (name tool-k))
                                                  tool-def])
                                               tools))]
                 (merge acc prefixed-tools))

               ;; Simple prefixed wildcard: {:plane/* 'namespace}
               (and (keyword? k) (str/ends-with? (name k) "/*"))
               (let [prefix (subs (name k) 0 (- (count (name k)) 2))
                     ns-sym (if (symbol? v) v (:namespace v))
                     tools (or (get-namespace-tools ns-sym)
                               (register-namespace! ns-sym))
                     prefixed-tools (into {}
                                          (map (fn [[tool-k tool-def]]
                                                 [(keyword (str prefix "/" (name tool-k)))
                                                  tool-def])
                                               tools))]
                 (merge acc prefixed-tools))

               :else acc))
           {}
           wildcards)]

      ;; Normalize explicit tools (supports direct function references)
      ;; Then merge: explicit tools override wildcards
      (merge expanded-wildcards (normalize-tools-map (into {} explicit))))))

(defn auto-discover!
  "Auto-discover and register tool namespaces matching a pattern.
  
  Scans all loaded namespaces for those matching the pattern and
  containing a `register-tools!` function.
  
  Example:
    (auto-discover! #\".*\\.pyjama\\.tools$\")
  
  Returns a map of namespace -> tool-count."
  [ns-pattern]
  (let [matching-nses (filter #(re-matches ns-pattern (str (ns-name %)))
                              (all-ns))
        results (atom {})]
    (doseq [ns matching-nses]
      (when (ns-resolve ns 'register-tools!)
        (try
          (let [tools (register-namespace! (ns-name ns))]
            (swap! results assoc (ns-name ns) (count tools)))
          (catch Exception e
            (binding [*out* *err*]
              (println (str "⚠️  Failed to auto-register " (ns-name ns) ": " (.getMessage e))))))))
    @results))

(comment
  ;; Example usage

  ;; 1. Register a namespace manually
  (register-namespace! 'plane-client.pyjama.tools)

  ;; 2. Auto-discover all tool namespaces
  (auto-discover! #".*\.pyjama\.tools$")

  ;; 3. Get all registered tools
  (all-tools)

  ;; 4. Expand wildcards in agent EDN
  (expand-wildcard-tools
   {:* 'plane-client.pyjama.tools
    :custom-tool {:fn 'my-ns/my-fn}})

  ;; 5. Use prefixed wildcards
  (expand-wildcard-tools
   {:plane/* 'plane-client.pyjama.tools
    :email/* 'email-client.tools.registry}))
