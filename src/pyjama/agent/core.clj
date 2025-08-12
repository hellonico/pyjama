(ns pyjama.agent.core
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

(defn- looks-like-template? [s]
 (boolean (re-find pyjama.io.template/token-re s)))

(defn render-step-prompt [step ctx params]
 (let [tpl (:prompt step)]
  (cond
   ;; Step has a literal prompt and it isn't templated: return as-is.
   (and (string? tpl) (not (looks-like-template? tpl)) (not (str/blank? tpl)))
   tpl

   ;; Step has a templated prompt: render, but never allow it to become "".
   (and (string? tpl) (looks-like-template? tpl))
   (let [rendered (pyjama.io.template/render-template tpl ctx params)]
    (if (str/blank? rendered) tpl rendered))

   ;; No step prompt → inherit running prompt if available
   :else (or (:prompt ctx) (:prompt params) ""))))


;; --- merge strategies -------------------------------------------------------
(defmulti merge-par (fn [_ctx _params k _branches] k))

(defn- obs->text [o]
 (cond
  (nil? o) ""
  (string? o) o
  (map? o) (or (:text o)
               (when-let [v (:merged o)] (:text v))
               (when-let [v (:content o)] v)
               (when-let [v (:choices o)] (str v))
               (pr-str o))
  :else (pr-str o)))

(defmethod merge-par :concat-texts [_ctx _params _ branches]
 (let [order (keys branches)
       body (->> order
                 (map (fn [k]
                       (let [t (-> branches (get k) :obs obs->text)]
                        (when (seq (str t))
                         (str "### " (name k) "\n" t)))))
                 (remove nil?)
                 (clojure.string/join "\n\n"))]
  {:text body}))

(defmethod merge-par :keep-map [_ _ _ branches] branches)
(defmethod merge-par :default [_ _ _ branches] {:parallel branches})


;; --- run a subgraph from a step id until :done or a terminal? ---------------
(declare run-step)
(declare decide-next)

(defn- run-subgraph
 "Runs a branch starting at start-id on a copy of ctx.
  Returns {:obs (:last-obs end-ctx) :trace (:trace end-ctx)}."
 [spec start-id ctx params]
 (loop [c (assoc ctx :trace [])
        sid start-id
        n 0]
  (if (or (= sid :done) (>= n (or (:max-steps spec) 20)))
   {:obs (:last-obs c) :trace (:trace c)}
   (let [c' (run-step spec sid c params)
         c'' (update c' :trace (fnil conj []) {:step sid :obs (:last-obs c')})
         nid (decide-next spec sid c'')]
    (recur c'' nid (inc n))))))

(defn- run-fork
 [spec {:keys [parallel join] :as step} ctx params]
 ;(binding [*out* *err*]
 ; (println "runfork" "parallel=" parallel "join=" join))

 (let [branch-ids (vec (or parallel []))
       ;_ (binding [*out* *err*]
       ;   (println "fork> launching branches:" branch-ids))
       futs (mapv (fn [bid]
                   [bid (future (let [r (run-subgraph spec bid ctx params)]
                                 ;(binding [*out* *err*]
                                 ; (println "fork> finished" bid "obs keys" (keys (:obs r))))
                                 r))])
                  branch-ids)
       strategy (get-in step [:join :strategy] :all)

       ;; EAGER join (force all derefs we decide to keep)
       results (case strategy
                :any (let [[bid f] (first futs)
                           r @f]
                      {bid r})
                :race (let [pairs (map (fn [[bid f]] [bid (deref f 5000 ::timeout)]) futs)
                            win (some (fn [[bid r]]
                                       (when (and (map? r)
                                                  (map? (:obs r))
                                                  (or (= :ok (get-in r [:obs :status]))
                                                      (string? (get-in r [:obs :text]))))
                                        [bid r]))
                                      pairs)
                            [bid r] (or win (first pairs))]
                       {bid (if (= ::timeout r) {:obs {:status :timeout}} r)})
                ;; default: wait all branches
                :all (into {} (map (fn [[bid f]] [bid @f]) futs)))]

  ;(binding [*out* *err*]
  ; (println "fork> results keys:" (keys results)))

  (let [merge-k (get-in step [:join :merge] :concat-texts)
        merged (merge-par ctx params merge-k results)
        ;_ (binding [*out* *err*]
        ;   (println "fork> merged keys:" (keys merged)
        ;            "preview:" (some-> (:text merged) (subs 0 (min 120 (count (:text merged)))))))
        obs {:status   :ok
             :parallel results
             :merged   merged}]
   (assoc ctx :last-obs obs))))


(defn- run-step [{:keys [steps tools] :as spec} step-id ctx params]
 (prn "▶︎" step-id)
 (let [{:keys [tool parallel] :as step} (get steps step-id)]
  (cond tool
        (let [{:keys [fn args] :as tool-spec} (get tools tool)

              base-args (merge args (:args step))
              ;; render ALL args deeply (single-token → raw value, multi-token → string)
              rendered (pyjama.io.template/render-args-deep base-args ctx params)
              ;_ (binding [*out* *err*] (println "→ TOOL" tool "RENDERED:" rendered))
              ;_ (binding [*out* *err*]
              ;   (println "→ TOOL" tool
              ;            "MESSAGE=" (pr-str (subs (str message) 0 (min 120 (count (str message))))))
              ;   (println "           ARGS =" (pr-str (dissoc targs :ctx :params))))
              ;

              ;; build message (keep whatever you already had)
              msg (cond
                   (:message step) (:message step)
                   (:message-path step) (get-in ctx (:message-path step))
                   (:message-template step) (pyjama.io.template/render-template (:message-template step) ctx params)
                   :else (or (get-in ctx [:last-obs :text])
                             (when (string? (:last-obs ctx)) (:last-obs ctx))
                             (pr-str (:last-obs ctx))))

              targs (merge rendered {:message msg :ctx ctx :params params})
              ;_ (binding [*out* *err*] (println "→ TOOL" tool "ARGS" (dissoc targs :ctx :params)))

              raw ((resolve-fn* fn) targs)
              ;_ (binding [*out* *err*] (println "   RAW     →" (pr-str raw)))

              ;; NO COERCION HERE — pass through
              obs (as-obs raw)
              ;_     (binding [*out* *err*] (println "   AS-OBS  →" (pr-str obs)))

              ;; record last obs + hoist files (for easy retrieval fallback)
              ctx' (assoc ctx :last-obs obs)
              ctx'' (if-let [fs (:files obs)] (assoc ctx' :project-files fs) ctx')]
         ctx'')

        ;; NEW: fork/join branch
        (and (vector? parallel) (seq parallel))
        (run-fork spec step ctx params)

        :else
        (let [;_ (prn ">>" step-id ">" step)
              ;_ (binding [*out* *err*]
              ;   (println "STEP" step-id "→ has-step-prompt?"
              ;            (boolean (and (string? (:prompt step)) (seq (:prompt step)))))
              ;   (when (and (string? (:prompt step)) (seq (:prompt step)))
              ;    (println "STEP" step-id "prompt-preview:"
              ;             (:prompt step))))
              ;(subs (:prompt step) 0 (min 60 (count (:prompt step)))))))
              final-prompt (render-step-prompt step ctx params)
              ;; only pass LLM-relevant keys from step
              llm-step (apply dissoc step step-non-llm-keys)
              ;; step keys override params, but we set :prompt last to avoid clobber
              llm-input (-> (merge params llm-step)
                            (assoc :prompt final-prompt
                                   :id step-id))
              raw (pyjama.core/call* llm-input)
              obs (as-obs raw)]
         (assoc ctx :last-obs obs)))))


(defn- get-path [ctx ks]
 (cond
  (nil? ks) nil

  ;; [:obs ...] → read from last obs
  (and (sequential? ks) (= :obs (first ks)))
  (get-in ctx (into [:last-obs] (rest ks)))

  ;; [:trace idx ...] → read from recorded trace
  (and (sequential? ks) (= :trace (first ks)))
  (let [[_ i & more] ks
        tr (:trace ctx)
        n  (count tr)
        idx (if (number? i) (if (neg? i) (+ n i) i) i)]
   (get-in (nth tr (or idx 0) {}) more))

  ;; [:ctx ...] → explicit ctx path
  (and (sequential? ks) (= :ctx (first ks)))
  (get-in ctx (rest ks))

  ;; generic vector path into ctx
  (sequential? ks)
  (get-in ctx ks)

  ;; bare keyword on ctx
  :else (get ctx ks)))

(defn- numify [v]
 (cond
  (number? v) (double v)
  (string? v) (try (Double/parseDouble (clojure.string/trim v))
                   (catch Exception _ ##NaN))
  :else ##NaN))

(defn- truthy* [v]
 (cond
  (nil? v) false
  (string? v) (not (clojure.string/blank? v))
  (sequential? v) (boolean (seq v))
  (map? v) (boolean (seq v))
  :else (boolean v)))



(defmulti eval-cond (fn [_ctx op & _] op))

(defmethod eval-cond := [ctx _ lhs rhs]
 (= (get-path ctx lhs) rhs))

(defmethod eval-cond :in [ctx _ lhs coll]
 (contains? (set coll) (get-path ctx lhs)))

(defmethod eval-cond :< [ctx _ lhs rhs]
 (<  (numify (get-path ctx lhs))
     (numify (get-path ctx rhs))))

(defmethod eval-cond :<= [ctx _ lhs rhs]
 (<= (numify (get-path ctx lhs))
     (numify (get-path ctx rhs))))

(defmethod eval-cond :> [ctx _ lhs rhs]
 (>  (numify (get-path ctx lhs))
     (numify (get-path ctx rhs))))

(defmethod eval-cond :>= [ctx _ lhs rhs]
 (>= (numify (get-path ctx lhs))
     (numify (get-path ctx rhs))))

(defmethod eval-cond :and [ctx _ & xs]
 (every? (comp truthy* #(get-path ctx %)) xs))

(defmethod eval-cond :or [ctx _ & xs]
 (some    (comp truthy* #(get-path ctx %)) xs))

(defmethod eval-cond :not [ctx _ x]
 (not (truthy* (get-path ctx x))))

;; Optional, handy sometimes
(defmethod eval-cond :contains [ctx _ coll x]
 (let [c (get-path ctx coll)
       v (get-path ctx x)]
  (cond
   (map? c)       (contains? c v)
   (string? c)    (and (string? v) (clojure.string/includes? c v))
   (sequential? c)(some #{v} c)
   :else false)))

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
              (vector? w) (boolean (#'eval-when-dsl ctx w))
              (ifn? w) (boolean (w ctx))
              (nil? w) true                                 ;; keep/flip depending on your policy
              :else false)]
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
     ;(clojure.pprint/pprint ctx)
     (if (or (= step-id :done) (>= n (or max-steps 20)))
      (:last-obs ctx)
      (let [ctx' (run-step spec step-id ctx params)         ;; run the step, update :last-obs
            ctx'' (update ctx' :trace (fnil conj [])
                          {:step step-id :obs (:last-obs ctx')}) ;; record new obs
            next-id (decide-next spec step-id ctx'')]
       (recur ctx'' next-id (inc n)))))))))