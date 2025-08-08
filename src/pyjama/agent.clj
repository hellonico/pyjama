(ns pyjama.agent
 (:require
  [clojure.edn :as edn]
  [pyjama.core]
  [pyjama.io.template]
  [pyjama.utils :as utils]))

;; Normalize any step result into a map so downstream logic is stable.
(defn- as-obs [x]
 (cond
  (map? x) x
  (string? x) {:text x}
  :else {:value x}))


(defn- last-text [ctx]
 (or (:text (:last-obs ctx))
     (when (string? (:last-obs ctx)) (:last-obs ctx))
     (pr-str (:last-obs ctx))))

(defn- tool-args
 "Build the args for a tool step:
  - :message: defaults to last LLM text
  - allow overrides via step keys :message, :message-path, :message-template"
 [step ctx params base-args]
 (let [{:keys [message message-path message-template]} step
       ;; message candidates by priority
       msg (cond
            message message
            message-path (get-in ctx message-path)
            message-template (pyjama.io.template/render-template
                              message-template ctx params)
            :else (last-text ctx))]
  (merge base-args {:message msg :ctx ctx :params params})))

(defn resolve-fn*
 "Return a Var (IFn) for EDN :fn, or throw with context."
 [f]
 (cond
  (var? f) f
  (symbol? f)
  (let [v (try
           (requiring-resolve f)
           (catch Throwable e
            (throw (ex-info "Cannot requiring-resolve tool fn"
                            {:fn f} e))))]
   (when-not (var? v)
    (throw (ex-info "Resolved value is not a Var"
                    {:fn f :resolved v :class (some-> v class str)})))
   v)
  (ifn? f) f
  :else
  (throw (ex-info "Tool :fn must be a symbol, Var, or IFn" {:fn f}))))

(defn explain-tool [agent-spec tool-k]
 (let [{:keys [fn args] :as tool} (get-in agent-spec [:tools tool-k])]
  (binding [*out* *err*]
   (println "— Checking tool" tool-k)
   (println "   Spec:" tool)
   (println "   :fn class:" (some-> fn class str)))
  (let [v (resolve-fn* fn)
        m (meta v)]
   (binding [*out* *err*]
    (println "   Resolved:" v)
    (println "   Is Var?:" (var? v) "IFn?:" (ifn? v))
    (println "   Meta ns/name:" (some-> (:ns m) ns-name str) "/" (str (:name m)))
    (println "   Default args:" args))
   v)))

(defn coerce-formatted
 "If step declares :format {:type :edn} and obs is a string, parse it."
 [step obs]
 (let [{:keys [type]} (:format step)]
  (cond
   (and (= type :edn) (string? obs))
   (try (edn/read-string obs)
        (catch Exception e
         (binding [*out* *err*]
          (println "⚠️  Could not parse EDN:" (pr-str obs) (.getMessage e)))
         obs))

   :else obs)))


(defn- run-step [{:keys [steps tools]} step-id ctx params]
 (let [{:keys [tool] :as step} (get steps step-id)]
  (if tool
   (let [{:keys [fn args] :as tool-spec} (get tools tool)]

    ;(binding [*out* *err*]
    ; (println "TRACE last two →" (take-last 2 (:trace ctx)))
    ; (println "NOTIFY TEMPLATE →" (:message-template (get-in @pyjama.core/agents-registry [:news-analyzer :steps :notify-result]))))

    (when-not tool-spec
     (throw (ex-info "Unknown tool" {:tool tool :available (keys tools)})))
    (let [f (resolve-fn* fn)
          targs (tool-args step ctx params (or args {}))
          raw (binding [*out* *err*] (f targs))
          obs (-> raw                                       ;; parses EDN if declared
                  as-obs)]                                  ;; {:text "..."} / map
     (assoc ctx :last-obs obs)))
   (let [prompt (merge step {:prompt (or (:prompt ctx) (:prompt params))
                             :id     step-id})
         raw (pyjama.core/call* prompt)
         obs (-> raw
                 as-obs)
         ;_ (clojure.pprint/pprint raw)
         ;_ (prn obs)
         ;_ (prn (class obs) "+" (class raw))
         ;_ (prn (:sentiment obs) )
         ;_ (prn (keys obs) )
         ]
    ;(assoc ctx :last-obs (-> raw
    ;                         (coerce-formatted step)
    ;                         as-obs))
    (assoc ctx :last-obs obs)
    ))))


(defn- eval-pred [ctx pred]
 ;; Tiny DSL: [:= [:obs :answer] "yes"] etc.
 (let [[op l r] pred
       lv (case l
           [:obs & ks] (get-in ctx (cons :last-obs (rest l)))
           l)]
  (case op
   := (= lv r)
   (throw (ex-info "Unknown op" {:pred pred})))))

(defn- decide-next [{:keys [steps controller]} step-id ctx]
 (let [{:keys [next routes terminal?]} (get steps step-id)]
  (cond
   terminal? :done
   next next
   (seq routes)
   (or (some (fn [route]
              (cond
               (contains? route :else)
               (:else route)

               :else
               (let [pred (:when route)]
                (when (eval-pred ctx pred)
                 (:next route)))))
             routes)
       :done)
   controller
   ;; Ask controller LLM to pick a next step from allowed (non-terminal) steps
   (let [choices (->> steps (remove (comp :terminal? val)) (map key))
         {:keys [choice]} (pyjama.core/call* (merge controller {:prompt {:obs     (:last-obs ctx)
                                                                         :choices choices}}))]
    (keyword choice))
   :else :done)))

(defn call
 "Agentic entry point: supports graphs, tools, and conditional routing."
 [{:keys [id] :as params}]
 (let [agent (get @pyjama.core/agents-registry id)]
  (if (vector? agent)
   ;; keep your legacy linear flow
   (reduce (fn [prev-output step-id]
            (pyjama.core/call* (merge params {:prompt prev-output :id step-id})))
           (:prompt (utils/templated-prompt params))
           agent)

   ;; agent graph
   (let [{:keys [start max-steps] :as spec} agent]
    (loop [ctx {:trace [] :prompt (:prompt params)}
           step-id start
           n 0]
     (if (or (= step-id :done) (>= n (or max-steps 20)))
      ctx
      (let [ctx' (run-step spec step-id ctx params)         ;; run the step, update :last-obs
            ctx'' (update ctx' :trace (fnil conj [])
                          {:step step-id :obs (:last-obs ctx')}) ;; record new obs
            next-id (decide-next spec step-id ctx'')]
       (recur ctx'' next-id (inc n)))))))))