(ns pyjama.tools.utils)

(defn passthrough
  "Pass arguments through unchanged, used for control flow steps."
  [args]
  (assoc args :status :ok))

(defn sleep
  "Sleep for a specified duration in milliseconds.
  
  Args:
    :duration-ms - Duration to sleep in milliseconds (default: 1000)
    :message - Optional message to display (default: 'Sleeping...')
  
  Returns:
    {:status :ok :slept-ms <duration>}"
  [{:keys [duration-ms message] :or {duration-ms 1000}}]
  (let [msg (or message (str "Sleeping for " duration-ms "ms..."))]
    (println msg)
    (Thread/sleep duration-ms)
    {:status :ok
     :slept-ms duration-ms
     :text msg}))
