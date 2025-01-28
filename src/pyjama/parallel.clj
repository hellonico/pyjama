(ns pyjama.parallel
  (:require [clojure.core.async :as async]
            [pyjama.core]))

(defn process-task [app-state task-id task-params task-atom]
  (swap! task-atom merge task-params)
  (let [result (pyjama.core/ollama (:url @app-state) :generate @task-atom :response)]
    (swap! task-atom assoc :result result)
    result))

(defn integrate-task [app-state task-id task-atom]
  (swap! app-state update-in [:tasks task-id] (fn [_] @task-atom)))

(defn process-tasks [app-state tasks]
  (let [task-atoms (mapv (fn [task] (atom {:id (:id task) :params (:params task) :result nil})) tasks)
        task-chan (async/chan)]                             ;; Channel to send tasks
    ;; Enqueue tasks onto the channel
    (doseq [[task task-atom] (map vector tasks task-atoms)]
      (async/go
        (integrate-task app-state (:id task) task-atom)     ;; Integrate task
        (let [result (process-task app-state (:id task) (:params task) task-atom)]
          (integrate-task app-state (:id task) task-atom)   ;; Integrate task
          (async/>! task-chan {:id (:id task) :result result})))) ;; Send result to the channel
    {:task-atoms task-atoms :task-chan task-chan}))


(defn clean-state [app-state]
  (swap! app-state assoc :tasks {}))

(defn parallel-generate [app-state config callback-one callback-all]
  (clean-state app-state)
  (let [{:keys [models pre prompts]} config
        tasks (map-indexed (fn [i [model prompt]]
                             {:id i :params (cond-> {:model model :prompt prompt}
                                                    pre (assoc :pre pre))})
                           (for [model models prompt prompts]
                             [model prompt]))
        ;; Process tasks and get the task channel
        {:keys [task-atoms task-chan]} (process-tasks app-state tasks)
        result-chan (async/chan)]                           ;; Channel to collect all results
    ;; Collect and process task results
    (async/go
      (loop [results []]
        (if (< (count results) (count tasks))
          (let [{:keys [id result]} (async/<! task-chan)]
            (callback-one {:id id :result result})          ;; Trigger callback-one
            (recur (conj results {:id id :result result}))) ;; Continue collecting
          (do
            (callback-all results)                          ;; Trigger callback-all when all are done
            (async/close! result-chan)))))                  ;; Close the channel when done
    nil))                                                   ;; Return nil since everything is handled asynchronously
