(ns pyjama.state
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [pyjama.models]
            [pyjama.image :refer :all]
            [pyjama.core]))

(defn update-state [state path _fn]
  (swap! state update-in path
         (constantly
           _fn)))

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
        format-data (when (:format @state)
                      (:format @state))
        request-params (cond-> {:stream true
                                :model  (:model @state)
                                :prompt (:prompt @state)}
                               image-data (assoc :images image-data)
                               format-data (assoc
                                             :format format-data
                                             ;:stream false
                                             )
                               )
        result-ch (async/thread
                    (swap! state assoc :processing true)
                    (pyjama.core/ollama (:url @state) :generate request-params _fn)
                    (swap! state assoc :processing false)
                    )]
    (async/go
      ; close the messaging channel once the function has finished.
      (let [_ (async/<! result-ch)]
        (async/close! ch)))
    (async/go-loop []
                   (if-let [val (async/<! ch)]
                     (if (:processing @state)
                       (do
                         (response-handler val)
                         (recur)))))))

(defn update-response [state text]
  (swap! state update :response str text))

(defn handle-submit [state]
  (swap! state assoc :response "")
  (ollama-request state (partial update-response state)))

(defn pull-model-stream [state model-name]
  (swap! state update-in [:pull :model] (constantly model-name))
  (let [ch (async/chan)
        _fn (partial pyjama.core/pipe-pull-tokens ch)
        result-ch (async/go
                    (pyjama.core/ollama (:url @state) :pull {:stream true :model model-name} _fn))]
    (async/go
      (let [_ (async/<! result-ch)]
        (async/close! ch)))
    ; Update UI with values from the channel
    (future
      (loop []
        (when-let [val (async/<!! ch)]
          (swap! state update-in [:pull :status] (constantly val)) ; Update state with the latest value
          (recur))))))