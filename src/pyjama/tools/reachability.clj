(ns pyjama.tools.reachability
 ;; Require all the tool namespaces so their publics exist at runtime
 (:require [pyjama.tools.ctx]
           [pyjama.tools.eval]
           [pyjama.tools.file]
           [pyjama.tools.movie]
           [pyjama.tools.notify]
           [pyjama.tools.pandoc]
           [pyjama.tools.retrieve]
           [pyjama.tools.web]
           [pyjama.tools.wiki]))

(def ^:private tool-ns-syms
 ['pyjama.tools.ctx
  'pyjama.tools.eval
  'pyjama.tools.file
  'pyjama.tools.movie
  'pyjama.tools.notify
  'pyjama.tools.pandoc
  'pyjama.tools.retrieve
  'pyjama.tools.web
  'pyjama.tools.wiki])

;; Keep every public Var reachable (prevents tree-shaking).
(defonce ^:private _keep-all-publics
         (vec
          (mapcat
           (fn [ns-sym]
            ;; ensure loaded (safe even if already loaded)
            (require ns-sym)
            ;; collect Vars of all publics; holding them in a top-level def
            ;; is enough for Graal reachability
            (vals (ns-publics ns-sym)))
           tool-ns-syms)))

;; Optional: a registry you can use for dynamic lookups without relying on requiring-resolve.
(def public-registry
 "Map from fully-qualified symbol -> Var for all public functions in pyjama.tools.*"
 (into {}
       (mapcat
        (fn [ns-sym]
         (for [[sym v] (ns-publics ns-sym)]
          [(symbol (str ns-sym) (name sym)) v]))
        tool-ns-syms)))
