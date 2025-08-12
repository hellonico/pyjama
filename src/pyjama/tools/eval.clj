(ns pyjama.tools.eval)

(defn compile-fn [{:keys [code fn-name]}]
 (try
  (let [ns-sym (gensym "mm.evo")
        full   (str "(ns " ns-sym ")\n" code "\n")
        _      (load-string full)
        f-sym  (symbol (str ns-sym) fn-name)
        f      (requiring-resolve f-sym)]
   (if (fn? f)
    {:status :ok :result f :code code :ns ns-sym}
    {:status :error :message "Function not found after compile"}))
  (catch Exception e
   {:status :error :message (.getMessage e) :code code})))