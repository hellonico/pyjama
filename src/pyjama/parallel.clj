(ns pyjama.parallel
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [pyjama.core]
            [pyjama.models :as m]))

(defn compute-times [results total-time]
  (let [;; Flatten all nested :result maps into a sequence
        extracted-results (map :result results)
        ;total-time (reduce + (map :duration-ms extracted-results))
        count-results (count extracted-results)
        avg-per-result (if (pos? count-results) (/ total-time count-results) 0)

        ;; Compute averages grouped by model
        model-groups (group-by :model extracted-results)
        avg-per-model (into {}
                            (map (fn [[model res]]
                                   [model (/ (reduce + (map :duration-ms res)) (count res))])
                                 model-groups))

        ;; Compute averages grouped by URL
        url-groups (group-by :url extracted-results)
        avg-per-url (into {}
                          (map (fn [[url res]]
                                 [url (/ (reduce + (map :duration-ms res)) (count res))])
                               url-groups))]

    ;; Return results as a map
    {:total-time     total-time
     :count-results  count-results
     :avg-per-result avg-per-result
     :avg-per-model  avg-per-model
     :avg-per-url    avg-per-url}))

(defn get-next-url [app-state]
  (let [urls (:urls @app-state)
        idx (mod (inc (:url-index @app-state)) (count urls))
        _ (swap! app-state assoc :url-index idx)            ;; Update :url-index in app-state
        ]
    (nth urls idx)
    ))


(defn process-task [app-state task-id task-params task-atom]
  (swap! task-atom merge task-params)
  (let [url (get-next-url app-state)
        start-time (System/nanoTime)                        ;; Record start time
        result (pyjama.core/ollama url :generate @task-atom :response)
        end-time (System/nanoTime)                          ;; Record end time
        duration-ms (/ (- end-time start-time) 1e6)]        ;; Convert nanoseconds to milliseconds
    (swap! task-atom assoc :result result :url url :duration-ms duration-ms)
    @task-atom))

(defn integrate-task [app-state task-id task-atom]
  (swap! app-state update-in [:tasks task-id] (fn [_] @task-atom)))
;
;(defn process-tasks [app-state tasks]
;  (let [task-atoms (mapv (fn [task] (atom {:id (:id task) :params (:params task) :result nil})) tasks)
;        task-chan (async/chan)]
;    ;; Enqueue tasks onto the channel
;    (doseq [[task task-atom] (map vector tasks task-atoms)]
;      (async/go
;        (integrate-task app-state (:id task) task-atom)     ;; Integrate task
;        (let [result (process-task app-state (:id task) (:params task) task-atom)]
;          (integrate-task app-state (:id task) task-atom)   ;; Integrate task
;          (async/>! task-chan {:id (:id task) :result result})))) ;; Send result to the channel
;    {:task-atoms task-atoms :task-chan task-chan}))

(defn process-tasks [app-state tasks & {:keys [concurrency] :or {concurrency 2}}]
  (let [task-atoms (mapv (fn [task] (atom {:id (:id task) :params (:params task) :result nil})) tasks)
        task-chan (async/chan)
        sem (async/chan concurrency)]                       ;; Channel acts as a semaphore
    ;; Fill the semaphore channel with `concurrency` tokens
    (dotimes [_ concurrency] (async/>!! sem :token))

    (async/go-loop [[task & remaining-tasks] (map vector tasks task-atoms)]
      (when task
        (let [[task-data task-atom] task]
          (async/go
            (integrate-task app-state (:id task-data) task-atom)
            (async/<! sem)                                  ;; Take a token from the semaphore (blocks if empty)
            (let [result (process-task app-state (:id task-data) (:params task-data) task-atom)]
              (integrate-task app-state (:id task-data) task-atom)
              (async/>! task-chan {:id (:id task-data) :result result})
              (async/>! sem :token)))                       ;; Release a token back into the semaphore
          (recur remaining-tasks))))                        ;; Continue processing remaining tasks
    {:task-atoms task-atoms :task-chan task-chan}))

(defn clean-state [app-state]
  (swap! app-state assoc
         :result-times []
         :tasks {}))

(defn parallel-generate [app-state config callback-one callback-all]
  (clean-state app-state)
  (let [{:keys [models pre prompts system format options]} config
        start-time (System/nanoTime)
        urls (or
               (and (:url config) (clojure.string/split (:url config) #","))
               (:urls config)
               (:urls @app-state)
               (and (:url @app-state) (clojure.string/split (:url @app-state) #","))
               [(System/getenv "OLLAMA_URL")]
               (and (System/getenv "OLLAMA_URLS") (clojure.string/split (System/getenv "OLLAMA_URLS") #","))
               ["http://localhost:11434"]
               )
        ;_ (println urls)
        _ (swap! app-state assoc :urls urls :url-index 0)   ;; Store URLs in state
        first-url (get-next-url app-state)
        _models (m/local-models-strip-latest first-url models)
        _ (if (empty? _models) (println "model " (str/join "," models) " missing on:" first-url))
        ; TODO this works only for :generate, try to generalize to other ollama API functions
        tasks (map-indexed (fn [i [model prompt]]
                             {:id     i
                              :params (cond-> {:model model :prompt prompt}
                                              pre (assoc :pre pre)
                                              options (assoc :options options)
                                              system (assoc :system system)
                                              format (assoc :format format))
                              })
                           (for [model _models prompt prompts]
                             [model prompt]))
        ;; Process tasks and get the task channel
        {:keys [task-atoms task-chan]} (process-tasks app-state tasks {:concurrency (count urls)})
        result-chan (async/chan)]                           ;; Channel to collect all results
    ;; Collect and process task results
    (async/go
      (loop [results []]
        (if (< (count results) (count tasks))
          (let [{:keys [id result]} (async/<! task-chan)]
            (callback-one {:id id :result result})          ;; Trigger callback-one
            (recur (conj results {:id id :result result}))) ;; Continue collecting
          (do

            (swap! app-state assoc :result-times (compute-times results (/ (- (System/nanoTime) start-time) 1e6)))
            (println (:result-times @app-state))

            (callback-all results)
            (async/close! result-chan))))
      )
    nil))


(defn pgen [config]
  (let [app-state (atom {:processing true :url (:url config) :tasks {}})]
    (parallel-generate
      app-state
      config
      identity
      (fn [_] (swap! app-state assoc :processing false)))
    (while (:processing @app-state)
      (Thread/sleep 500))
    ;TODO: i just broke something somewhere with vals
    (vals (:tasks @app-state))))
(def generate pgen)