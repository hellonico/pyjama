(ns pyjama.agents
  (:require [clojure.core.async :as async]
            [pyjama.core]
            [pyjama.functions :refer [ollama-fn]]))

(defn create-broker
  []
  (let [topics (atom {})]
    {:topics      topics
     :subscribe   (fn [topic subscriber-chan]
                    (swap! topics update topic #(conj (or % #{}) subscriber-chan)))
     :unsubscribe (fn [topic subscriber-chan]
                    (swap! topics update topic #(disj (or % #{}) subscriber-chan)))
     :publish     (fn [topic message]
                    (doseq [subscriber (get @topics topic)]
                      (async/put! subscriber message)))}))

(defn make-ollama-agent
  [id broker config callback]
  (let [input-chan (async/chan)
        ollama-helper (ollama-fn config)]
    (async/go-loop []
                   (let [message (async/<! input-chan)]
                     (when message
                       (try
                         (callback message ollama-helper broker)
                         (catch Exception e
                           (println "Error processing Ollama response:" (.getMessage e)))))
                     (recur)))
    {:id         id
     :input-chan input-chan}))