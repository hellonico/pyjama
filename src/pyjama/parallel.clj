(ns pyjama.parallel
  (:require [pyjama.core]))

(defn process-task [app-state task-id task-params task-atom]
  (swap! task-atom merge task-params)
  (let [result (pyjama.core/ollama (:url @app-state) :generate @task-atom :response)]
    (swap! task-atom assoc :result result)
    result))

(defn integrate-task [app-state task-id task-atom]
  (swap! app-state update-in [:tasks task-id] (fn [_] @task-atom)))

(defn process-tasks [app-state tasks]
  (let [task-atoms (mapv (fn [task] (atom {:id (:id task) :params (:params task) :result nil})) tasks)
        futures (mapv (fn [[task task-atom]]
                        (future
                          (let [result (process-task app-state (:id task) (:params task) task-atom)]
                            (integrate-task app-state (:id task) task-atom))))
                      (map vector tasks task-atoms))]
    {:task-atoms task-atoms :futures futures}))

(defn parallel-generate [app-state config callback-one callback-all]
  (let [{:keys [models pre prompts]} config
        tasks (map-indexed (fn [i [model prompt]]
                             {:id i :params (cond-> {:model model :prompt prompt}
                                                    pre (assoc :pre pre))})
                           (for [model models prompt prompts]
                             [model prompt]))]
    (let [{:keys [task-atoms futures]} (process-tasks app-state tasks)]
      (doseq [f futures]
        (Thread/sleep 1000)
        (callback-one @f))))
  (callback-all))