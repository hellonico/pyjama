(ns pyjama.agent.core
  (:require
   [clojure.core :as core]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
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

(defn resolve-fn*
  "Return a Var (IFn) for EDN :fn, or throw with context."
  [f]
  (cond
    (var? f) f
    (symbol? f)
    (let [v (try
              (requiring-resolve f)
              (catch Throwable e
                (.printStackTrace e)
                (throw (ex-info "Cannot requiring-resolve tool fn"
                                {:fn f} e))))]
      (when-not (var? v)
        (throw (ex-info "Resolved value is not a Var"
                        {:fn f :resolved v :class (some-> v class str)})))
      v)
    (ifn? f) f
    :else
    (throw (ex-info "Tool :fn must be a symbol, Var, or IFn" {:fn f}))))

(defn validate-all-tools
  "Check all tools and print warnings for missing implementations."
  [tools]
  (let [missing (reduce-kv (fn [acc k {:keys [fn]}]
                             (if (and fn (symbol? fn))
                               (try
                                 (if (var? (requiring-resolve fn))
                                   acc
                                   (conj acc [k fn]))
                                 (catch Throwable _
                                   (conj acc [k fn])))
                               acc))
                           []
                           tools)]
    (when (seq missing)
      (binding [*out* *err*]
        (println "\n⚠️  WARNING: The following tool implementation functions were not found:")
        (doseq [[k f] (sort-by first missing)]
          (println (str "   • " k " -> " f)))
        (println)))))

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
    obs))


(def step-non-llm-keys
  #{:tool :routes :next :terminal? :message :message-path :message-template})

(defn- looks-like-template? [s]
  (boolean (re-find pyjama.io.template/token-re s)))

(defn- kebab-to-snake [s]
  (str/replace (name s) "-" "_"))

(defn- load-prompt [p step-id]
  (cond
    ;; Explicit resource (old style) or explicit dynamic tag
    (and (string? p) (str/starts-with? p "resource:"))
    (let [path-suffix (subs p 9) ;; "resource:".length
          path (if (= "dynamic" path-suffix)
                 (str "prompts/" (kebab-to-snake step-id) ".md")
                 path-suffix)]
      (if-let [res (io/resource path)]
        (slurp res)
        (throw (ex-info (str "Prompt resource not found: " path) {:path path}))))

    ;; No prompt specified -> try to load from convention
    (nil? p)
    (let [path (str "prompts/" (kebab-to-snake step-id) ".md")]
      (if-let [res (io/resource path)]
        (slurp res)
        ;; If not found, return nil so we can fall back to inheritance or empty
        nil))

    ;; Literal prompt
    :else p))

(defn render-step-prompt [step-id step ctx params]
  (let [tpl (load-prompt (:prompt step) step-id)]
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
  (println "▶︎" (:id ctx) "▶︎" step-id)
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

                targs (merge {:message msg} rendered {:ctx ctx :params params})
              ;_ (binding [*out* *err*] (println "→ TOOL" tool "ARGS" (dissoc targs :ctx :params)))

                raw ((resolve-fn* fn) targs)
              ;_ (binding [*out* *err*] (println "   RAW     →" (pr-str raw)))

                ;; NO COERCION HERE — pass through
                obs (as-obs raw)
              ;_     (binding [*out* *err*] (println "   AS-OBS  →" (pr-str obs)))

                ;; record last obs + hoist files (for easy retrieval fallback)
                ctx' (-> ctx
                         (assoc :last-obs obs)
                         ;; merge files hoist, if present
                         (cond-> (:files obs) (assoc :project-files (:files obs)))
                         ;; ✅ NEW: merge ctx mutations from tools
                         (cond-> (:set obs) (merge (:set obs))))]
            ctx')

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
                final-prompt (render-step-prompt step-id step ctx params)
                ;; only pass LLM-relevant keys from step
              ;llm-step (apply dissoc step step-non-llm-keys)
                llm-step (apply dissoc step step-non-llm-keys)
                ;; NEW: render all templatable fields in the LLM step (impl/model/url/temperature/etc)
                llm-step-rendered (pyjama.io.template/render-args-deep llm-step ctx params)
                llm-input (-> (merge params llm-step-rendered)
                              (assoc :prompt final-prompt
                                     :id step-id))
                raw (pyjama.core/call* llm-input)
                obs (as-obs raw)]
            (assoc ctx :last-obs obs)))))


(defn- pathlike? [x]
  (or (sequential? x) (keyword? x)))


(defn- get-path [ctx ks]
  (cond

    (nil? ks) nil
    (number? ks) ks

    ;; [:obs ...] → read from last obs
    (and (sequential? ks) (= :obs (first ks)))
    (get-in ctx (into [:last-obs] (rest ks)))

    ;; [:trace idx ...] → read from recorded trace
    (and (sequential? ks) (= :trace (first ks)))
    (let [[_ i & more] ks
          tr (:trace ctx)
          n (count tr)
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
;
;
;(defn- pathlike? [x]
; (or (sequential? x) (keyword? x)))

(defn- val-or-path [ctx x]
  (if (pathlike? x) (get-path ctx x) x))


(defmulti eval-cond (fn [_ctx op & _] op))

(defmethod eval-cond := [ctx _ lhs rhs]
  (= (get-path ctx lhs) rhs))

(defmethod eval-cond :in [ctx _ lhs coll]
  (contains? (set coll) (get-path ctx lhs)))

(defmethod eval-cond :< [ctx _ lhs rhs]
  (< (numify (get-path ctx lhs))
     (numify (get-path ctx rhs))))

(defmethod eval-cond :<= [ctx _ lhs rhs]
  (<= (numify (get-path ctx lhs))
      (numify (get-path ctx rhs))))

(defmethod eval-cond :> [ctx _ lhs rhs]
  (> (numify (get-path ctx lhs))
     (numify (get-path ctx rhs))))

(defmethod eval-cond :>= [ctx _ lhs rhs]
  (>= (numify (get-path ctx lhs))
      (numify (get-path ctx rhs))))

(defmethod eval-cond :and [ctx _ & xs]
  (every? (comp truthy* #(get-path ctx %)) xs))

(defmethod eval-cond :or [ctx _ & xs]
  (some (comp truthy* #(get-path ctx %)) xs))

(defmethod eval-cond :not [ctx _ x]
  (not (truthy* (get-path ctx x))))

;; Optional, handy sometimes
(defmethod eval-cond :contains [ctx _ coll x]
  (let [c (get-path ctx coll)
        v (get-path ctx x)]
    (cond
      (map? c) (contains? c v)
      (string? c) (and (string? v) (clojure.string/includes? c v))
      (sequential? c) (some #{v} c)
      :else false)))

(defmethod eval-cond :nonempty [ctx _ lhs]
  (let [v (get-path ctx lhs)]
    (cond
      (string? v) (not (str/blank? v))
      (sequential? v) (boolean (seq v))
      (map? v) (boolean (seq v))
      :else (some? v))))

(defmethod eval-cond :truthy [ctx _ lhs]
  (truthy* (get-path ctx lhs)))

(defmethod eval-cond :default [_ctx op & _]
  (throw (ex-info "Unknown routing op" {:op op})))

(defn- eval-when-dsl [ctx v]                                ;; v like [:= [:obs :status] :test]
  (let [[op & args] v]
    (apply eval-cond ctx op args)))

(defn eval-route [ctx route]
  (let [{w :when nxt :next :as r} route]
    (cond
      (vector? w) (when (#'eval-when-dsl ctx w) nxt)
      (ifn? w) (when (w ctx) nxt)
      (nil? w) nxt
      :else nil)))


(defn decide-next [{:keys [steps]} step-id ctx]
  (let [{:keys [routes next]} (get steps step-id)]
    (if (seq routes)
      (let [candidates (keep #(eval-route ctx %) routes)
            hit (first (remove nil? candidates))
            fallback (some (fn [r] (when (contains? r :else) (:else r))) (reverse routes))]
        (or hit fallback next :done))
      (or next :done))))

(defn visualize
  "Generate a simple ASCII flow diagram for an agent graph.
   Returns nil (prints to stdout)."
  [agent-id spec]
  (let [start (:start spec)
        steps (:steps spec)]
    (println (str "\n[Flow] Diagram for: " agent-id "\n"))

    (loop [current start
           visited #{}]
      (if (or (= current :done) (nil? current))
        (println "   [Done]")
        (if (contains? visited current)
          (println "   (Loop) detected to" current)
          (let [step (get steps current)]
            (println (str "   * " (name current)))
            (cond
              (:parallel step)
              (let [branches (:parallel step)]
                (println "     | [Parallel Execution]")
                (doseq [b branches]
                  (println (str "     |-- " (name b))))
                (println "     | [Join]"))

              (:routes step)
              (do
                (println "     ? Decision:")
                (doseq [r (:routes step)]
                  (when (:next r)
                    (println (str "     |-- [When " (pr-str (:when r)) "] -> " (name (:next r))))))
                (if-let [fallback (some :else (:routes step))]
                  (println (str "     +-- [Else] -> " (name fallback)))
                  (println (str "     +-- [Default] -> " (name (or (:next step) :done))))))

              :else
              (println "     |"))

            (if (:routes step)
              (if (:next step)
                (recur (:next step) (conj visited current))
                (println "   (End of linear trace, multiple paths possible)"))
              (recur (:next step) (conj visited current)))))))))

(defn call
  "Agentic entry point: supports graphs, tools, and conditional routing."
  [{:keys [id] :as params}]
  (let [registry @pyjama.core/agents-registry
        agent (get registry id)]
    (if (vector? agent)
      ;; keep your legacy linear flow
      (reduce (fn [prev-output step-id]
                (pyjama.core/call* (merge params {:prompt prev-output :id step-id})))
              (:prompt (utils/templated-prompt params))
              agent)

      ;; agent graph
      (let [;; MERGE GLOBAL TOOLS
            default-tools (get registry :tools)
            agent-tools (:tools agent)
            merged-tools (merge default-tools agent-tools)

            ;; MERGE COMMON STEPS
            ;; Automatically include standard lifecycle steps if defined in proper registry root
            common-steps (:common-steps registry)
            merged-steps (merge common-steps (:steps agent))

            spec (assoc agent :tools merged-tools
                        :steps merged-steps)

            {:keys [start max-steps]} spec]

        (validate-all-tools merged-tools)

        (loop [ctx (merge {:id id :trace [] :prompt (:prompt params) :original-prompt (:prompt params)} params)
               step-id start
               n 0]
          (if (or (= step-id :done) (>= n (or max-steps 20)))
            (:last-obs ctx)
            (let [ctx' (run-step spec step-id ctx params)         ;; run the step, update :last-obs
                  ctx'' (update ctx' :trace (fnil conj [])
                                {:step step-id :obs (:last-obs ctx')}) ;; record new obs
                  next-id (decide-next spec step-id ctx'')]
              (recur ctx'' next-id (inc n)))))))))