(ns pyjama.tools.notify)

(defn notify-user
 "Send a message/data to the user and return an observation."
 [{:keys [channel message ctx params] :as m}]
 ;(binding [*out* *err*]
 ; (println "TRACE (last 3):" (take-last 3 (:trace ctx))))
 (case (keyword channel)
  :stdout                                                   ;(binding [*out* *err*]
  (do (println "[NOTIFY]" message) {:status :ok :notified true})
  {:status :unknown-channel :channel channel}))

