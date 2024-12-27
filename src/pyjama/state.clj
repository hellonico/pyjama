(ns pyjama.state
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [pyjama.models]
            [pyjama.image :refer :all]
            [pyjama.core]))

(defn update-state [state path _fn]
  (swap! state update-in path _fn))

(defn local-models [state]
  (update-state
    state
    [:local-models]
    (pyjama.core/ollama
      (@state :url)
      :tags {}
      (fn [res]
        (map #(str/replace (:name %) #":.*" "") (:models res))))))

(defn remote-models [state]
  (update-state state [:models]
                (pyjama.models/fetch-remote-models)))


(defn ollama-request [state response-handler]
  (let [ch (async/chan 10)
        _fn (partial pyjama.core/pipe-chat-tokens ch)
        image-data (when (:images @state)
                     (map pyjama.image/image-to-base64 (:images @state)))
        request-params (cond-> {:stream true
                                :model  (:model @state)
                                :prompt (:prompt @state)}
                               image-data (assoc :images image-data))
        result-ch (async/thread
                    (swap! state assoc :processing true)
                    (pyjama.core/ollama (:url @state) :generate request-params _fn)
                    (swap! state assoc :processing false)
                    )]
    (async/go
      (let [_ (async/<! result-ch)]
        (async/close! ch)))
    (async/go-loop []
                   (if-let [val (async/<! ch)]
                     (do
                       (response-handler val)
                       (recur))))))

(defn update-response [state text]
  (swap! state update :response str text))

(defn handle-submit [state]
  (swap! state assoc :response "")
  (ollama-request state (partial update-response state)))
