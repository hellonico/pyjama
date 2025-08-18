(ns pyjama.agent-test
 (:require
  [clojure.test :refer :all]
  [clojure.edn :as edn]
  [pyjama.tools.retrieve]
  [pyjama.tools.file]
  [pyjama.io.template]
  [pyjama.agent.core :as agent]))

;; --- Test helpers ------------------------------------------------------------

(def captured (atom nil))

(defn sample-tool
 "A tool fn that records its args, returns a map with text and files."
 [{:keys [message] :as args}]
 (reset! captured args)
 {:text  (str "TOOL:" message)
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
         (agent/coerce-formatted {:format {:type :edn}} "{:oops")))) ;; parse failure → unchanged
 (testing "no format → unchanged"
  (is (= "[1 2]" (agent/coerce-formatted {} "[1 2]")))))

(deftest routing-conditions
 (let [ctx {:last-obs {:status :ok}
            :v        42
            :xs       [1]
            :m        {:k 1}}]
  (is (true? (agent/eval-cond ctx := [:obs :status] :ok)))
  (is (false? (agent/eval-cond ctx := [:obs :status] :err)))
  (is (true? (agent/eval-cond ctx :in [:v] [10 42 99])))
  (is (true? (agent/eval-cond ctx :nonempty [:xs])))
  (is (true? (agent/eval-cond ctx :nonempty [:m])))
  (is (false? (agent/eval-cond ctx :nonempty [:missing])))))

(deftest eval-route-precedence
 (let [ctx {:last-obs {:status :ok}}]
  (is (= :n1 (agent/eval-route ctx {:when [:= [:obs :status] :ok] :next :n1 :else :n2})))
  (is (= :n2 (agent/eval-route ctx {:when [:= [:obs :status] :err] :next :n1 :else :n2})))
  (is (nil? (agent/eval-route ctx {:when [:= [:obs :status] :err] :next :n1})))))

(deftest decide-next-ordering
 (let [spec {:steps {:s {:routes [{:when [:= [:obs :status] :ok] :next :a}]
                         :next   :b}}}]
  (is (= :a (agent/decide-next spec :s {:last-obs {:status :ok}})))
  (is (= :b (agent/decide-next spec :s {:last-obs {:status :nope}})))
  (is (= :done (agent/decide-next {:steps {:s {}}} :s {})))))

;; --- Deeper routing tests ----------------------------------------------------

(deftest get-path-behavior
 (let [ctx {:last-obs {:status :ok :payload {:n 7}}
            :plain    42
            :m        {:k 1}}]
  (testing "nil path"
   (is (nil? (#'pyjama.agent.core/get-path ctx nil))))
  (testing "[:obs ...] resolves into :last-obs"
   (is (= :ok (#'pyjama.agent.core/get-path ctx [:obs :status])))
   (is (= 7 (#'pyjama.agent.core/get-path ctx [:obs :payload :n]))))
  (testing "arbitrary seq path"
   (is (= 1 (#'pyjama.agent.core/get-path ctx [:m :k]))))
  (testing "non-seq key"
   (is (= 42 (#'pyjama.agent.core/get-path ctx :plain))))))

(deftest eval-when-dsl-ops
 (let [ctx {:last-obs {:status :ok}
            :v        42
            :xs       [1]
            :m        {:k 2}
            :s        ""}]
  (testing ":= compares extracted value to rhs"
   (is (true? (agent/eval-cond ctx := [:obs :status] :ok)))
   (is (false? (agent/eval-cond ctx := [:obs :status] :err))))
  (testing ":in checks membership"
   (is (true? (agent/eval-cond ctx :in [:v] [10 42])))
   (is (false? (agent/eval-cond ctx :in [:v] [10 11]))))
  (testing ":nonempty across types"
   (is (true? (agent/eval-cond ctx :nonempty [:xs])))
   (is (true? (agent/eval-cond ctx :nonempty [:m])))
   (is (false? (agent/eval-cond ctx :nonempty [:s])))
   (is (false? (agent/eval-cond ctx :nonempty [:missing]))))
  (testing "unknown op throws"
   (is (thrown-with-msg? clojure.lang.ExceptionInfo
                         #"Unknown routing op"
                         (agent/eval-cond ctx :gt [:v] 10))))))

(deftest eval-when-dsl-wrapper
 (let [ctx {:last-obs {:status :ok}}]
  (is (true? (#'pyjama.agent.core/eval-when-dsl ctx [:= [:obs :status] :ok])))
  (is (false? (#'pyjama.agent.core/eval-when-dsl ctx [:= [:obs :status] :nope])))))

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
  (is (nil? (agent/eval-route ctx {:when [:= [:obs :status] :err]
                                   :next :n1})))))

(deftest eval-route-with-function-when
 (let [ctx {:last-obs {:status :ok}}
       pred-true (fn [c] (= :ok (get-in c [:last-obs :status])))
       pred-false (fn [_] false)
       pred-truthy (fn [_] :yep)                            ;; non-boolean but truthy
       pred-nil (fn [_] nil)]                               ;; falsey
  (is (= :go (agent/eval-route ctx {:when pred-true :next :go :else :no}))
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
                           {:when (fn [c] (> (:n c) 0)) :next :ok}
                           {:when [:= [:obs :status] :ok] :next :late-true}]
                  :next   :fallback}}}]
  (is (= :ok (agent/decide-next spec :s ctx))
      "Second route passes first; short-circuits before later true routes")))

(deftest decide-next-else-vs-next
 (let [ctx {:last-obs {:status :err}}
       spec {:steps
             {:s {:routes [{:when [:= [:obs :status] :ok] :next :good :else :from-else}] ;; this route fails → returns :else
                  :next   :fallback}}}]
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

;; ── Agent-spec integration tests for :clj-project ─────────────────────────────
(deftest clj-project-doc-flow
 (let [!load (atom nil)
       !retrieve (atom nil)
       !classify (atom nil)
       !write (atom nil)

       ;; Stub tools (Vars so agent doesn't need requiring-resolve)
       read-code-base (fn [{:keys [dir] :as targs}]
                       (reset! !load targs)
                       {:text  "INDEX"
                        :files ["src/a.clj" "src/b.clj"]})
       pick-snippets (fn [{:keys [files message] :as targs}]
                      (reset! !retrieve targs)
                      (is (vector? files) "files must be a vector")
                      (is (= "Build docs" message))
                      {:text "SNIPPETS"})
       classify (fn [{:keys [message] :as targs}]
                 (reset! !classify targs)
                 (cond
                  (re-find #"TEST-CASE" message) {:status :test-case :topic message}
                  (re-find #"FEATURE" message) {:status :feature :topic message}
                  (re-find #"REORG" message) {:status :reorg :topic message}
                  :else {:status :document :topic message}))
       write-file (fn [{:keys [dir name message] :as targs}]
                   (reset! !write targs)
                   {:status :written
                    :path   (str dir "/" name)
                    :body   message})]

  (with-redefs
   ;; Minimal template engine for this spec
   [pyjama.io.template/render-args-deep
    (fn [args ctx params]
     (into {}
           (for [[k v] args]
            [k (cond
                (= v "{{ctx.project-dir}}")
                (or (:project-dir ctx) (:project-dir params) ".")

                (= v "{{trace[0].obs.files}}")
                (get-in ctx [:trace 0 :obs :files])

                (= v "{{params.project-dir}}/docs")
                (str (:project-dir params) "/docs")

                (= v "{{params.project-dir}}/test")
                (str (:project-dir params) "/test")

                (= v "{{params.project-dir}}/src")
                (str (:project-dir params) "/src")

                :else v)])))
    pyjama.io.template/render-template
    (fn [tpl ctx params]
     (cond
      (= tpl "{{prompt}}") (:prompt params)
      (= tpl "{{obs.text}}") (or (get-in ctx [:last-obs :text]) (get-in ctx [:last-obs]))
      :else tpl))

    ;; Stub LLM responses per step id
    pyjama.core/call* (fn [{:keys [id]}]
                       (case id
                        :doc "DOC-MD"
                        :test "TEST-CODE"
                        :reorg "REORG-MD"
                        :feature "FEATURE-CODE"
                        ;; Default echo so we can see accidental calls
                        {:echo id}))

    ;; Provide the agent spec with local tool fns (no requiring-resolve needed)
    pyjama.core/agents-registry
    (atom
     {:clj-project
      {:start     :load
       :max-steps 8
       :steps
       {:load       {:tool :read-code-base
                     :args {:dir "{{ctx.project-dir}}"}
                     :next :retrieve}
        :retrieve   {:tool             :pick-snippets
                     :message-template "{{prompt}}"
                     :args             {:files     "{{trace[0].obs.files]]" ;; intentionally wrong to ensure we *don’t* use this path
                                        :max-files 6
                                        :max-chars 8000}
                     :next             :classify}
        ;; fix the typo above for real rendering path via explicit override below
        :classify   {:tool             :classify
                     :message-template "{{prompt}}"
                     :routes
                     [{:when [:in [:obs :status] [:test :test-case]] :next :test}
                      {:when [:= [:obs :status] :document] :next :doc}
                      {:when [:= [:obs :status] :reorg] :next :reorg}
                      {:when [:in [:obs :status] [:feature :new-feature]] :next :feature}
                      {:else :doc}]}
        :doc        {:prompt "ignored-doc-template-uses-LLM"
                     :next   :write-md}
        :test       {:prompt "ignored-test-template-uses-LLM"
                     :next   :write-test}
        :reorg      {:prompt "ignored-reorg-template-uses-LLM"
                     :next   :write-md}
        :feature    {:prompt "ignored-feature-template-uses-LLM"
                     :next   :write-src}
        :write-md   {:tool             :write-file
                     :message-template "{{obs.text}}"
                     :args             {:dir "{{params.project-dir}}/docs" :name "generated.md"}
                     :terminal?        true}
        :write-test {:tool             :write-file
                     :message-template "{{obs.text}}"
                     :args             {:dir "{{params.project-dir}}/test" :name "generated_test.clj"}
                     :terminal?        true}
        :write-src  {:tool             :write-file
                     :message-template "{{obs.text}}"
                     :args             {:dir "{{params.project-dir}}/src" :name "generated.clj"}
                     :terminal?        true}}
       :tools
       ;; inside your with-redefs agent spec
       {:read-code-base {:fn read-code-base}                ;; was #'read-code-base
        :pick-snippets  {:fn (fn [{:keys [ctx] :as targs}]
                              (pick-snippets (assoc targs :files (get-in ctx [:trace 0 :obs :files]))))}
        :classify       {:fn classify}                      ;; was #'classify
        :write-file     {:fn write-file}}                   ;; was #'write-file

       }})]

   ;; Run DOC flow
   (let [out (agent/call {:id :clj-project :project-dir "." :prompt "Build docs"})]
    ;; Final write
    (is (= {:status :written
            :path   "./docs/generated.md"
            :body   "DOC-MD"}
           out))
    ;; Tool-call assertions
    (is (= "." (:dir @!load)))
    (is (= ["src/a.clj" "src/b.clj"] (:files @!retrieve)))
    (is (= "Build docs" (:message @!retrieve)))
    (is (= "Build docs" (:message @!classify)))
    (is (= "./docs" (-> @!write :dir)))
    (is (= "generated.md" (-> @!write :name)))
    (is (= "DOC-MD" (-> @!write :message)))))))

(deftest clj-project-test-branch
 ;; Classifier sees "TEST-CASE" and routes to :test → :write-test
 (let [!write (atom nil)]
  (with-redefs
   [pyjama.io.template/render-args-deep (fn [a _ p] (into {} (for [[k v] a] [k (if (= v "{{params.project-dir}}/test") (str (:project-dir p) "/test") v)])))
    pyjama.io.template/render-template (fn [tpl ctx params] (if (= tpl "{{obs.text}}") (get-in ctx [:last-obs :text]) tpl))
    pyjama.tools.retrieve/read-code-base (fn [_] {:files []})
    pyjama.tools.retrieve/pick-snippets (fn [_] {:text ""})
    pyjama.tools.retrieve/classify (fn [{:keys [message]}] {:status :test-case :topic message})
    pyjama.tools.file/write-file (fn [targs] (reset! !write targs) {:status :ok}) ;; not using registry variant here; simpler stubs
    pyjama.core/call* (fn [{:keys [id]}] (case id :test "TEST-CODE" {:echo id}))
    pyjama.core/agents-registry (atom
                                 {:clj-project
                                  {:start :classify
                                   :steps {:classify   {:tool :classify :message-template "{{prompt}}" :next :test}
                                           :test       {:prompt "ignored" :next :write-test}
                                           :write-test {:tool             :write-file
                                                        :message-template "{{obs.text}}"
                                                        :args             {:dir "{{params.project-dir}}/test" :name "generated_test.clj"}
                                                        :terminal?        true}}
                                   :tools {:classify   {:fn `pyjama.tools.retrieve/classify}
                                           :write-file {:fn `pyjama.tools.file/write-file}}}})]
   (let [_ (agent/call {:id :clj-project :project-dir "/p" :prompt "TEST-CASE: add coverage"})
         w @!write]
    (is (= "/p/test" (:dir w)))
    (is (= "generated_test.clj" (:name w)))
    (is (= "TEST-CODE" (:message w)))))))

(deftest clj-project-feature-and-reorg-branches
 (let [!writes (atom [])]
  (with-redefs
   [pyjama.io.template/render-args-deep (fn [a _ p]
                                         (into {} (for [[k v] a]
                                                   [k (cond
                                                       (= v "{{params.project-dir}}/src") (str (:project-dir p) "/src")
                                                       (= v "{{params.project-dir}}/docs") (str (:project-dir p) "/docs")
                                                       :else v)])))
    pyjama.io.template/render-template
    (fn [tpl ctx params]
     (case tpl
      "{{prompt}}" (:prompt params)
      "{{obs.text}}" (get-in ctx [:last-obs :text])
      tpl))

    pyjama.tools.retrieve/classify (fn [{:keys [message]}]
                                    (cond
                                     (re-find #"FEATURE" message) {:status :feature :topic message}
                                     (re-find #"REORG" message) {:status :reorg :topic message}))
    pyjama.tools.file/write-file (fn [targs] (swap! !writes conj targs) {:status :ok})
    pyjama.core/call* (fn [{:keys [id]}]
                       (case id
                        :feature "FEATURE-CODE"
                        :reorg "REORG-MD"
                        {:echo id}))
    pyjama.core/agents-registry (atom
                                 {:clj-project
                                  {:start :classify
                                   :steps {:classify  {:tool   :classify :message-template "{{prompt}}"
                                                       :routes [{:when [:= [:obs :status] :feature] :next :feature}
                                                                {:when [:= [:obs :status] :reorg] :next :reorg}]}
                                           :feature   {:prompt "ignored" :next :write-src}
                                           :reorg     {:prompt "ignored" :next :write-md}
                                           :write-src {:tool             :write-file
                                                       :message-template "{{obs.text}}"
                                                       :args             {:dir "{{params.project-dir}}/src" :name "generated.clj"}
                                                       :terminal?        true}
                                           :write-md  {:tool             :write-file
                                                       :message-template "{{obs.text}}"
                                                       :args             {:dir "{{params.project-dir}}/docs" :name "generated.md"}
                                                       :terminal?        true}}
                                   :tools {:classify   {:fn `pyjama.tools.retrieve/classify}
                                           :write-file {:fn `pyjama.tools.file/write-file}}}})]
   ;; FEATURE flow
   (agent/call {:id :clj-project :project-dir "/p" :prompt "FEATURE: do X"})
   ;; REORG flow
   (agent/call {:id :clj-project :project-dir "/p" :prompt "REORG: split modules"})
   (let [[w1 w2] @!writes]
    (is (= "/p/src" (:dir w1)))
    (is (= "generated.clj" (:name w1)))
    (is (= "FEATURE-CODE" (:message w1)))

    (is (= "/p/docs" (:dir w2)))
    (is (= "generated.md" (:name w2)))
    (is (= "REORG-MD" (:message w2)))))))
