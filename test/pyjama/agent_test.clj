(ns pyjama.agent-test
  (:require
    [clojure.test :refer :all]
    [clojure.edn :as edn]
    [pyjama.agent :as agent]))

;; --- Test helpers ------------------------------------------------------------

(def captured (atom nil))

(defn sample-tool
  "A tool fn that records its args, returns a map with text and files."
  [{:keys [message] :as args}]
  (reset! captured args)
  {:text (str "TOOL:" message)
   :files ["f1" "f2"]})

(defn sample-ifn
  "IFn (not a Var) to validate resolve-fn* accepts IFn."
  [{:keys [message]}]
  {:text (str "IFN:" message)})

(defn throwing-tool
  [_]
  (throw (ex-info "boom" {})))

;; --- Unit tests --------------------------------------------------------------

(deftest as-obs-normalization
  (is (= {:status :empty} (agent/as-obs nil)))
  (is (= {:text "hi"} (agent/as-obs "hi")))
  (is (= {:x 1} (agent/as-obs {:x 1})))
  (is (= {:text "42"} (agent/as-obs 42))))

(deftest coerce-formatted-edn
  (testing "parses EDN string when format requests EDN"
    (is (= {:a 1}
           (agent/coerce-formatted {:format {:type :edn}} "{:a 1}"))))
  (testing "leaves non-strings / non-edn alone"
    (is (= 7 (agent/coerce-formatted {:format {:type :edn}} 7)))
    (is (= "{:oops"
           (agent/coerce-formatted {:format {:type :edn}} "{:oops"))))  ;; parse failure → unchanged
  (testing "no format → unchanged"
    (is (= "[1 2]" (agent/coerce-formatted {} "[1 2]")))))

(deftest routing-conditions
  (let [ctx {:last-obs {:status :ok}
             :v 42
             :xs [1]
             :m {:k 1}}]
    (is (true?  (agent/eval-cond ctx := [:obs :status] :ok)))
    (is (false? (agent/eval-cond ctx := [:obs :status] :err)))
    (is (true?  (agent/eval-cond ctx :in [:v] [10 42 99])))
    (is (true?  (agent/eval-cond ctx :nonempty [:xs])))
    (is (true?  (agent/eval-cond ctx :nonempty [:m])))
    (is (false? (agent/eval-cond ctx :nonempty [:missing])))))

(deftest eval-route-precedence
  (let [ctx {:last-obs {:status :ok}}]
    (is (= :n1 (agent/eval-route ctx {:when [:= [:obs :status] :ok] :next :n1 :else :n2})))
    (is (= :n2 (agent/eval-route ctx {:when [:= [:obs :status] :err] :next :n1 :else :n2})))
    (is (nil?   (agent/eval-route ctx {:when [:= [:obs :status] :err] :next :n1})))))

(deftest decide-next-ordering
  (let [spec {:steps {:s {:routes [{:when [:= [:obs :status] :ok] :next :a}]
                          :next :b}}}]
    (is (= :a (agent/decide-next spec :s {:last-obs {:status :ok}})))
    (is (= :b (agent/decide-next spec :s {:last-obs {:status :nope}})))
    (is (= :done (agent/decide-next {:steps {:s {}}} :s {})))))

;; --- Deeper routing tests ----------------------------------------------------

(deftest get-path-behavior
  (let [ctx {:last-obs {:status :ok :payload {:n 7}}
             :plain 42
             :m {:k 1}}]
    (testing "nil path"
      (is (nil? (#'pyjama.agent/get-path ctx nil))))
    (testing "[:obs ...] resolves into :last-obs"
      (is (= :ok (#'pyjama.agent/get-path ctx [:obs :status])))
      (is (= 7    (#'pyjama.agent/get-path ctx [:obs :payload :n]))))
    (testing "arbitrary seq path"
      (is (= 1 (#'pyjama.agent/get-path ctx [:m :k]))))
    (testing "non-seq key"
      (is (= 42 (#'pyjama.agent/get-path ctx :plain))))))

(deftest eval-when-dsl-ops
  (let [ctx {:last-obs {:status :ok}
             :v 42
             :xs [1]
             :m {:k 2}
             :s ""}]
    (testing ":= compares extracted value to rhs"
      (is (true?  (agent/eval-cond ctx := [:obs :status] :ok)))
      (is (false? (agent/eval-cond ctx := [:obs :status] :err))))
    (testing ":in checks membership"
      (is (true?  (agent/eval-cond ctx :in [:v] [10 42])))
      (is (false? (agent/eval-cond ctx :in [:v] [10 11]))))
    (testing ":nonempty across types"
      (is (true?  (agent/eval-cond ctx :nonempty [:xs])))
      (is (true?  (agent/eval-cond ctx :nonempty [:m])))
      (is (false? (agent/eval-cond ctx :nonempty [:s])))
      (is (false? (agent/eval-cond ctx :nonempty [:missing]))))
    (testing "unknown op throws"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Unknown routing op"
                            (agent/eval-cond ctx :gt [:v] 10))))))

(deftest eval-when-dsl-wrapper
  (let [ctx {:last-obs {:status :ok}}]
    (is (true?  (#'pyjama.agent/eval-when-dsl ctx [:= [:obs :status] :ok])))
    (is (false? (#'pyjama.agent/eval-when-dsl ctx [:= [:obs :status] :nope])))))

(deftest eval-route-precedence-fixed
  (let [ctx {:last-obs {:status :ok}}]
    ;; When vector condition true, use :next (ignore :else)
    (is (= :n1 (agent/eval-route ctx {:when [:= [:obs :status] :ok]
                                      :next :n1
                                      :else :n2})))
    ;; When vector condition false, use :else if present
    (is (= :n2 (agent/eval-route ctx {:when [:= [:obs :status] :err]
                                      :next :n1
                                      :else :n2})))
    ;; When false and no else → nil
    (is (nil?   (agent/eval-route ctx {:when [:= [:obs :status] :err]
                                       :next :n1})))))

(deftest eval-route-with-function-when
  (let [ctx {:last-obs {:status :ok}}
        pred-true  (fn [c] (= :ok (get-in c [:last-obs :status])))
        pred-false (fn [_] false)
        pred-truthy (fn [_] :yep)   ;; non-boolean but truthy
        pred-nil    (fn [_] nil)]   ;; falsey
    (is (= :go (agent/eval-route ctx {:when pred-true  :next :go :else :no}))
        "IFn true → next")
    (is (= :no (agent/eval-route ctx {:when pred-false :next :go :else :no}))
        "IFn false → else")
    (is (= :go (agent/eval-route ctx {:when pred-truthy :next :go :else :no}))
        "Truthy value is treated as true")
    (is (= :no (agent/eval-route ctx {:when pred-nil :next :go :else :no}))
        "Nil is false → else")))

(deftest eval-route-unconditional-policy
  ;; Depending on how you chose to handle nil :when in your fix:
  ;; If you made (nil? w) => true (unconditional), this should be :go.
  ;; If you made (nil? w) => false, update the expected to :fallback.
  (let [ctx {}]
    (is (= :go (agent/eval-route ctx {:next :go}))
        "No :when means unconditional next (adjust if you prefer otherwise)")))

(deftest decide-next-multiple-routes-first-true-wins
  (let [ctx {:last-obs {:status :ok} :n 5}
        spec {:steps
              {:s {:routes [{:when [:= [:obs :status] :err] :next :bad}
                            {:when (fn [c] (> (:n c) 0))   :next :ok}
                            {:when [:= [:obs :status] :ok] :next :late-true}]
                   :next :fallback}}}]
    (is (= :ok (agent/decide-next spec :s ctx))
        "Second route passes first; short-circuits before later true routes")))

(deftest decide-next-else-vs-next
  (let [ctx {:last-obs {:status :err}}
        spec {:steps
              {:s {:routes [{:when [:= [:obs :status] :ok] :next :good :else :from-else}]  ;; this route fails → returns :else
                   :next :fallback}}}]
    (is (= :from-else (agent/decide-next spec :s ctx))
        "Else of the first (failing) route is used before the step-level :next")))

(deftest decide-next-no-routes-falls-back-to-next-or-done
  (is (= :nxt (agent/decide-next {:steps {:s {:next :nxt}}} :s {})))
  (is (= :done (agent/decide-next {:steps {:s {}}} :s {}))))
(deftest routing-integration-chooses-branches
  (with-redefs [pyjama.core/agents-registry
                (atom {:g {:start :a
                           :steps
                           {:a {:routes [{:when [:= [:obs :status] :ok] :next :b}
                                         {:when [:= [:obs :status] :err] :next :c :else :d}]}
                            :b {:next :done}
                            :c {:next :done}
                            :d {:next :done}}}})
                ;; Stub LLM calls so non-tool steps don't emit real text
                pyjama.core/call* (fn [_] nil)]
    ;; call will go :a -> LLM(nil) -> {:status :empty} -> route picks :d -> LLM(nil) -> {:status :empty} -> :done
    (is (= {:status :empty}
           (agent/call {:id :g :prompt "ignored"})))

    ;; Manual branch checks (independent of LLM behavior)
    (let [spec (get @pyjama.core/agents-registry :g)]
      (is (= :b (agent/decide-next spec :a {:last-obs {:status :ok}})))
      (is (= :c (agent/decide-next spec :a {:last-obs {:status :err}})))
      (is (= :d (agent/decide-next spec :a {:last-obs {:status :weird}}))))))


(deftest route-maps-don’t-leak-else-when-passing
  (let [ctx {:last-obs {:status :ok}}]
    (is (= :go (agent/eval-route ctx {:when [:= [:obs :status] :ok]
                                      :next :go
                                      :else :no}))
        "When passes, :else must be ignored")))


;(deftest resolve-fn*-variants
;  (testing "var resolves"
;    (let [v (agent/resolve-fn* #'sample-tool)]
;      (is (var? v))))
;  (testing "symbol requiring-resolve"
;    (let [v (agent/resolve-fn* `sample-tool)]
;      (is (var? v))
;      (is (= "sample-tool" (str (:name (meta v)))))))
;  (testing "IFn accepted"
;    (is (fn? (agent/resolve-fn* sample-ifn))))
;  (testing "bad input throws"
;    (is (thrown-with-msg? clojure.lang.ExceptionInfo
;                          #"Tool :fn must be a symbol, Var, or IFn"
;                          (agent/resolve-fn* 123)))))

;
;;; --- Integration around run-step / call -------------------------------------
;
;(deftest tool-step-exec-and-message-building
;  (reset! captured nil)
;  (with-redefs [;; Make template rendering deterministic and visible
;                pyjama.io.template/render-template (fn [tpl ctx params]
;                                                     (str "TPL:" tpl "|" (:x params)))
;                pyjama.io.template/render-args-deep (fn [args ctx params]
;                                                      ;; pretend deep render by turning keywords into strings
;                                                      (into {} (for [[k v] args]
;                                                                 [k (if (keyword? v) (name v) v)])))
;                ;; pyjama.core stubs
;                pyjama.core/agents-registry (atom
;                                              {:graph
;                                               {:start :t
;                                                :steps
;                                                {:t {:tool :echo}
;                                                 :done-step {:terminal? true}} ;; unused, just for shape
;                                                :tools
;                                                {:echo {:fn `sample-tool
;                                                        :args {:base :arg}}}})}]
;(let [params {:id :graph :x 99 :prompt "ROOT"}
;      ;; Simulate last-obs text to verify default :message sourcing
;      initial-ctx {:trace [] :prompt "ROOT" :last-obs {:text "prev"}}
;      step-id :t
;      spec (get @pyjama.core/agents-registry :graph)
;      ;; call private run-step through public call by assembling a one-step graph:
;      res (do
;            ;; Run the graph via public entry
;            (agent/call params))]
;  ;; call returns last-obs
;  (is (map? res))
;  (is (= "TOOL:prev" (:text res)))
;  ;; tool got merged args, plus message and ctx/params
;  (is (= "arg" (-> @captured :base)))  ;; rendered-args-deep turned :arg => "arg"
;  (is (= "prev" (-> @captured :message)))
;  (is (map? (-> @captured :ctx)))
;  (is (= 99 (-> @captured :params :x))))))
;
;(deftest tool-step-respects-message-template-and-hoists-files
;  (reset! captured nil)
;  (with-redefs [pyjama.io.template/render-template (fn [tpl ctx params]
;                                                     (str "MSG:" tpl ":" (:foo params)))
;                pyjama.io.template/render-args-deep identity
;                pyjama.core/agents-registry (atom
;                                              {:g {:start :tool
;                                                   :steps {:tool {:tool :echo
;                                                                  :message-template "hello {{foo}}"
;                                                                  :routes [{:when [:= [:obs :status] :ok] :next :done}]}}
;                                                   :tools {:echo {:fn `sample-tool}}}})]
;    (let [out (agent/call {:id :g :prompt "P" :foo "bar"})]
;      (is (= "TOOL:MSG:hello {{foo}}:bar" (:text out)))
;      ;; The call loop returns only last-obs, but during step execution
;      ;; the ctx should have hoisted files; we validate via what tool returned:
;      ;; (we can't directly access ctx here; instead ensure tool returned files were preserved in obs)
;      ;; Already validated by :text; ensure files attached:
;      (is (= ["f1" "f2"] (:files out))))))
;
;(deftest llm-step-executes-when-no-tool
;  (with-redefs [pyjama.core/call* (fn [m]                     ;; echo the LLM input
;                                    {:echo m})
;                pyjama.io.template/render-template (fn [tpl ctx params] (str "PROMPT:" tpl))
;                pyjama.core/agents-registry (atom
;                                              {:g {:start :llm
;                                                   :steps {:llm {:prompt "You are {{x}}" :temperature 0.1
;                                                                 :routes [{:when [:= [:obs :echo :id] :llm] :next :done}]}}}})]
;    (let [out (agent/call {:id :g :x "Nico" :prompt "ROOT"})]
;      (is (= :llm (get-in out [:echo :id])))
;      (is (= "PROMPT:You are {{x}}" (get-in out [:echo :prompt])))
;      (is (= 0.1 (get-in out [:echo :temperature])))
;      (is (= "ROOT" (get-in out [:echo :prompt])) "render-step-prompt inherits running prompt when no :prompt in step"))))
;
;(deftest legacy-linear-flow
;  (with-redefs [pyjama.core/call* (fn [{:keys [prompt id]}]
;                                    (str prompt "→" (name id)))
;                pyjama.utils/templated-prompt (fn [params] {:prompt "LEGACY"})
;                pyjama.core/agents-registry (atom
;                                              {:legacy [:a :b :c]})]
;    (is (= "LEGACY→a→b→c" (agent/call {:id :legacy :prompt "IGNORED"})))))
;
;(deftest graph-termination-conditions
;  (with-redefs [pyjama.core/agents-registry (atom
;                                              {:g {:start :s
;                                                   :max-steps 2
;                                                   :steps {:s {:next :s}}}})]
;    ;; Will loop s→s but stop after max-steps; last-obs is nil → as-obs would be handled inside run-step,
;    ;; but since no tool/llm is run (no :tool and not LLM step), call returns nil :last-obs.
;    (is (nil? (agent/call {:id :g :prompt ""})))))
;
;
;(deftest explain-tool-prints-and-resolves
;  (with-redefs [pyjama.core/agents-registry (atom nil)]
;    (let [agent-spec {:tools {:echo {:fn `sample-tool :args {:a 1}}}}
;          v (agent/explain-tool agent-spec :echo)]
;      (is (var? v))
;      (is (= "sample-tool" (str (:name (meta v)))))))
;
;  (testing "explain-tool with IFn"
;    (let [agent-spec {:tools {:f {:fn sample-ifn :args {}}}}]
;      (is (ifn? (agent/explain-tool agent-spec :f))))))
;
;(deftest resolve-error-context
;  (is (thrown-with-msg? clojure.lang.ExceptionInfo
;                        #"Tool :fn must be a symbol, Var, or IFn"
;                        (agent/resolve-fn* {:no :good}))))
