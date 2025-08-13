(ns pyjama.tools.ctx)

(defn merge! [{:keys [set ctx]}]
 ;; return what you want merged; your runner should merge into ctx
 ;(println "→ ←:" set)
 {:status :ok :set set})

(defn count-items
 "Return {:status :ok :n (count items)}"
 [{:keys [items]}]
 {:status :ok :n (count items)})

(defn select-index
 "Return {:status :ok :item (nth items i)} (nil-safe)"
 [{:keys [items i]}]
 (let [i (long (or i 0))]
  (if (and (sequential? items) (<= 0 i) (< i (count items)))
   {:status :ok :item (nth items i)}
   {:status :error :item nil :reason :index-out-of-bounds})))