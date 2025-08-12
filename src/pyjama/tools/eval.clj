(ns pyjama.tools.eval
 (:require [clojure.string :as str]))

(defn- normalize-spaces [s]
 (-> s
     (str/replace #"\u00A0|\u202F|\u2007" " ") ; kill NBSP variants
     (str/replace #"[ \t]+" " ")))

(defn compile-fn
 "Compile `code` (string with a defn) into a fresh, in-memory namespace and return an IFn.
  Options:
    :fn-name   - symbol or string naming the defn to resolve (e.g. \"lend-assets\")
    :ns-prefix - optional prefix for the generated ns (default \"mm.evo\")
    :requires  - optional seq of namespaces to require in the temp ns
    :refer-core? - bool, refer clojure.core (default true)"
 [{:keys [code fn-name ns-prefix requires refer-core?]
   :or   {ns-prefix "mm.evo" refer-core? true}}]
 (try
  (let [clean   (-> (or code "") normalize-spaces str/trim)
        ;; generate a unique ns symbol e.g. mm.evo1723456789123
        ns-sym  (symbol (str ns-prefix (System/currentTimeMillis)))
        _       (create-ns ns-sym)
        the-ns  (the-ns ns-sym)
        fname   (symbol (name fn-name))]

   ;; Load into the fresh ns in-memory (no disk file needed).
   (binding [*ns* the-ns]
    (when refer-core? (refer 'clojure.core))
    (doseq [r (or requires [])] (require r))
    ;; If code already has (ns ...) at the top, just eval it;
    ;; otherwise eval it as-is into this ns.
    (if (re-find #"(?m)^\s*\(ns\s" clean)
     (load-string clean)        ; code declares its own ns
     (load-string clean)))      ; code is just (defn ...)

   ;; Resolve the function from that ns
   (if-let [v (ns-resolve the-ns fname)]
    (let [f (if (var? v) @v v)]
     (if (ifn? f)
      {:status :ok :result f :var v :ns ns-sym :code clean}
      {:status :error :message (str "Resolved " fname " is not IFn")}))

    {:status :error :message (str "Function " fname " not found after compile")
     :ns ns-sym :code clean}))

  (catch Throwable e
   {:status :error
    :message (.getMessage e)
    :class   (some-> e class str)
    :code    code})))
