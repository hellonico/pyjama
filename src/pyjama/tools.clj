(ns pyjama.tools)

(defn notify-user
 "Send a message/data to the user and return an observation."
 [{:keys [channel message ctx params] :as m}]
 (case (keyword channel)
  :stdout (binding [*out* *err*]
           (println "[NOTIFY]" message) {:status :ok :notified true})
  {:status :unknown-channel :channel channel}))

