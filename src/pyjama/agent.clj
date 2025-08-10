(ns pyjama.agent
 (:require
  [clojure.core :as core]
  [clojure.edn :as edn]
  [clojure.string :as str]
  [pyjama.core]
  [pyjama.io.template]
  [pyjama.utils :as utils]))

;; Normalize any step result into a map so downstream logic is stable.
(defn as-obs [x]
 (cond
  (nil? x) {:status :empty}
  (string? x) {:text x}
  (map? x) x                                                ;; <- KEEP maps as-is
  :else {:text (pr-str x)}))

(defn- tool-args
 "Build the args for a tool step:
  - :message: defaults to last LLM text
  - allow overrides via step keys :message, :message-path, :message-template"
 [step ctx params base-args]
 (let [{:keys [message message-path message-template]} step
       msg (cond
            message message
            message-path (get-in ctx message-path)
            message-template (pyjama.io.template/render-template message-template ctx params)
            :else (or (get-in ctx [:last-obs :text])
                      (when (string? (:last-obs ctx)) (:last-obs ctx))
                      (pr-str (:last-obs ctx))))
       rendered-args (pyjama.io.template/render-args-deep (or base-args {}) ctx params)]
  (merge rendered-args {:message msg :ctx ctx :params params})))

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
 (if-let [fmt (:format step)]
  (let [{:keys [type]} (:format step)]
   (cond
    (and (= type :edn) (string? obs))
    (try (edn/read-string obs)
         (catch Exception e
          (binding [*out* *err*]
           (println "⚠️  Could not parse EDN:" (pr-str obs) (.getMessage e)))
          obs))

    :else obs))
  obs)
 )


(def step-non-llm-keys
 #{:tool :routes :next :terminal? :message :message-path :message-template})

(defn- render-step-prompt [step ctx params]
 (if-let [tpl (:prompt step)]
  ;; render the template in the step if present
  (pyjama.io.template/render-template tpl ctx params)
  ;; otherwise inherit the running prompt
  (or (:prompt ctx) (:prompt params) "")))

(defn- run-step [{:keys [steps tools]} step-id ctx params]
 (let [{:keys [tool] :as step} (get steps step-id)]
  (if tool
   (let [{:keys [fn args] :as tool-spec} (get tools tool)
         step-args (:args step)
         base-args (merge args step-args)
         ;; render ALL args deeply (single-token → raw value, multi-token → string)
         rendered  (pyjama.io.template/render-args-deep base-args ctx params)

         ;; build message (keep whatever you already had)
         msg (cond
              (:message step)             (:message step)
              (:message-path step)        (get-in ctx (:message-path step))
              (:message-template step)    (pyjama.io.template/render-template (:message-template step) ctx params)
              :else (or (get-in ctx [:last-obs :text])
                        (when (string? (:last-obs ctx)) (:last-obs ctx))
                        (pr-str (:last-obs ctx))))

         targs (merge rendered {:message msg :ctx ctx :params params})
         ;_     (binding [*out* *err*] (println "→ TOOL" tool "ARGS" (dissoc targs :ctx :params)))

         raw   ((resolve-fn* fn) targs)
         ;_     (binding [*out* *err*] (println "   RAW     →" (pr-str raw)))

         ;; NO COERCION HERE — pass through
         obs   (as-obs raw)
         ;_     (binding [*out* *err*] (println "   AS-OBS  →" (pr-str obs)))

         ;; record last obs + hoist files (for easy retrieval fallback)
         ctx'  (assoc ctx :last-obs obs)
         ctx'' (if-let [fs (:files obs)] (assoc ctx' :project-files fs) ctx')]
    ctx'')
   (let [final-prompt (render-step-prompt step ctx params)
         ;; only pass LLM-relevant keys from step
         llm-step    (apply dissoc step step-non-llm-keys)
         ;; step keys override params, but we set :prompt last to avoid clobber
         llm-input   (-> (merge params llm-step)
                         (assoc :prompt final-prompt
                                :id     step-id))
         raw         (pyjama.core/call* llm-input)
         obs         (as-obs raw)]
    (assoc ctx :last-obs obs)))))


(defn- get-path [ctx ks]
 (cond
  (nil? ks) nil
  (and (sequential? ks) (= :obs (first ks)))
  (get-in ctx (into [:last-obs] (rest ks)))
  (sequential? ks) (get-in ctx ks)
  :else (get ctx ks)))

(defmulti eval-cond (fn [_ctx op & _] op))

(defmethod eval-cond := [ctx _ lhs rhs]
 (= (get-path ctx lhs) rhs))

(defmethod eval-cond :in [ctx _ lhs coll]
 (contains? (set coll) (get-path ctx lhs)))

(defmethod eval-cond :nonempty [ctx _ lhs]
 (let [v (get-path ctx lhs)]
  (cond
   (string? v) (not (str/blank? v))
   (sequential? v) (boolean (seq v))
   (map? v) (boolean (seq v))
   :else (some? v))))

(defmethod eval-cond :default [_ctx op & _]
 (throw (ex-info "Unknown routing op" {:op op})))

(defn- eval-when-dsl [ctx v]                                ;; v like [:= [:obs :status] :test]
 (let [[op & args] v]
  (apply eval-cond ctx op args)))

(defn eval-route [ctx route]
 (let [{w :when nxt :next els :else :as r} route
       pass? (cond
              (vector? w) (boolean (#'pyjama.agent/eval-when-dsl ctx w))
              (ifn?   w) (boolean (w ctx))
              (nil?   w) true     ;; keep/flip depending on your policy
              :else        false)]
  (if pass?
   nxt
   (when (contains? r :else) els))))

(defn decide-next [{:keys [steps]} step-id ctx]
 (let [{:keys [routes next]} (get steps step-id)]
  (or (some identity (map #(eval-route ctx %) (or routes [])))
      next
      :done)))

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
    (loop [ctx {:trace [] :prompt (:prompt params) :original-prompt (:prompt params)}
           step-id start
           n 0]
     (prn id "▶︎" step-id)
     ;(clojure.pprint/pprint ctx)
     (if (or (= step-id :done) (>= n (or max-steps 20)))
      (:last-obs ctx)
      (let [ctx' (run-step spec step-id ctx params)         ;; run the step, update :last-obs
            ctx'' (update ctx' :trace (fnil conj [])
                          {:step step-id :obs (:last-obs ctx')}) ;; record new obs
            next-id (decide-next spec step-id ctx'')]
       (recur ctx'' next-id (inc n)))))))))