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

;
; LOCAL MODELS
(defn local-models
  "This fetches info on local models for the given :url. Models will be stored in :local-models."
  [state]
  (update-state
    state
    [:local-models]
    (pyjama.core/ollama
      (@state :url)
      :tags {}
      (fn [res]
        (map #(str/replace (:name %) #":.*" "") (:models res))))))

;
; REMOTE MODELS
(defn remote-models
  "This fetches models available on ollama.com and update the state with a :models key"
  [state]
  (update-state state [:models]
                (pyjama.models/fetch-remote-models)))


;
; GENERATE WITH CHANNELS AND DATA IN THE STATE
(defn ollama-generate [state response-handler]
  (let [ch (async/chan 10)
        _fn (partial pyjama.core/pipe-generate-tokens ch)
        image-data (when (:images @state)
                     (map pyjama.image/image-to-base64 (:images @state)))
        format-data (when (:format @state)
                      (:format @state))
        system-data (when (:system @state)
                      (:system @state))
        request-params (cond-> {:stream true
                                :model  (:model @state)
                                :system (:system @state)
                                :prompt (:prompt @state)}
                               image-data (assoc :images image-data)
                               system-data (assoc :system system-data)
                               format-data (assoc :format format-data))
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
  (ollama-generate state (partial update-response state)))

;
; CHAT
(defn ollama-chat [state response-handler]
  (swap! state assoc :processing true)
  ; make sure messages key is an array.
  ; has to be done beforehand
  ;(swap! state #(assoc % :messages (get % :messages [])))
  (let [ch (async/chan 10)
        _fn (partial pyjama.core/pipe-chat-tokens ch)
        ; TODO: where to handle images
        ;image-data (when (:images @state)
        ;             (map pyjama.image/image-to-base64 (:images @state)))
        format-data (when (:format @state)
                      (:format @state))
        system-data (when (:system @state)
                      (:system @state))
        request-params (cond-> {:stream true
                                :model  (:model @state)
                                :messages (:messages @state)}
                               system-data (assoc :system system-data)
                               format-data (assoc :format format-data)
                               )
        result-ch (async/thread
                    (pyjama.core/ollama (:url @state) :chat request-params _fn)
                    (swap! state assoc :processing false)
                    )]
    (async/go
      ; close the messaging channel once the function has finished.
      (let [_ (async/<! result-ch)]
        (async/close! ch)
        ; append the message to the list of messages
        (swap! state update :messages conj {:role :assistant :content (:response @state)})
        (swap! state assoc :response "")
        ))
    (async/go-loop []
      (if-let [val (async/<! ch)]
        (if (:processing @state)
          (do
            (response-handler val)
            (recur)))))))

(defn handle-chat [state]
  (swap! state assoc :response "")
  (ollama-chat state (partial update-response state)))


;
; MODEL PULL
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