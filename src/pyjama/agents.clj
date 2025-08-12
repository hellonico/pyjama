(ns ^:deprecated pyjama.agents
  "This used to be the main agents code, but now has been replaced by pyjama.agent.core"
  (:require [clojure.core.async :as async]
            [pyjama.core]
            [pyjama.functions :refer [ollama-fn]]))

(defn simple-log
  ([id category message]
   (println id "\t " category "\t" (apply str (subs (str message) 0 (min 100 (count (str message)))))))
  ([id message]
   (println id "\t " (apply str (subs (str message) 0 (min 100 (count (str message))))))))

(defn create-broker
  []
  (let [topics (atom {}) agents (atom {}) ]
    (letfn [
            (register [id agent]
              (swap! agents assoc id agent))

            (get-agent [id]
              (get @agents id))

            (subscribe [topic subscriber-chan]
              (swap! topics update topic #(conj (or % #{}) subscriber-chan)))

            (unsubscribe [topic subscriber-chan]
              (swap! topics update topic #(disj (or % #{}) subscriber-chan)))

            (publish [topic message]
              (async/go
                (doseq [subscriber (get @topics topic)]
                  (async/put! subscriber message))))

            (forward [from-topic to-topic]
              (let [forward-chan (async/chan)]
                (subscribe from-topic forward-chan)
                (async/go-loop []
                  (when-let [message (async/<! forward-chan)]
                    (publish to-topic message))
                  (recur))))]

      {
       :get-agent   get-agent
       :register    register

       :topics      topics
       :subscribe   subscribe
       :unsubscribe unsubscribe
       :publish     publish
       :forward     forward})))

(defn make-agent
  [id broker config callback]
  (let [input-chan (async/chan)
        output-chan (async/chan)]
    (async/go-loop []
      (let [message (async/<! input-chan)]
        (when message
          (try
            (simple-log id :in message)
            (let [out (callback message broker)]
              (if (not (nil? out))
                (do
                  (simple-log id :out out)
                  ((:publish broker) output-chan out)
                  ))
              )
            (catch Exception e
              (.printStackTrace e)
              (println "Error processing Agent:" (.getMessage e)))))
        (recur)))
    {:id          id
     :output-chan output-chan
     :input-chan  input-chan}))
(def make-simple-agent make-agent)

(defn make-ollama-agent
  [id broker config callback]
  (let [input-chan (async/chan)
        output-chan (async/chan)
        ollama-helper (ollama-fn config)]
    (async/go-loop []
      (let [message (async/<! input-chan)]
        (simple-log id :in message)
        (when message
          (try
            (let [out (callback message ollama-helper broker)]
              (simple-log id :out (apply str out))
              ((:publish broker) output-chan out))
            (catch Exception e
              (println "Error processing Ollama response:" (.getMessage e)))))
        (recur)))
    {:id          id
     :output-chan output-chan
     :input-chan  input-chan}))

(defn make-logger-agent [id broker config callback]
  (make-simple-agent id broker config (fn [message _] (println message) true)))

(defn make-echo-agent [id broker config callback]
  (make-simple-agent id broker config (fn [message _] message)))