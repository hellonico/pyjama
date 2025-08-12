(ns pyjama.tools.ctx)

(defn merge! [{:keys [set ctx]}]
 ;; return what you want merged; your runner should merge into ctx
 ;(println "→ ←:" set)
 {:status :ok :set set})