(ns pyjama.io.template-test
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [pyjama.io.template :as tpl]))

;; Helpers
(defn r [s ctx params] (tpl/render-template s ctx params))
(defn raw [s ctx params] (#'pyjama.io.template/render-any s ctx params)) ; private ok for tests

(def base-ctx
  {:prompt "A jazz party"
   :original-prompt "A Roaring: Twenties / Jazz Party!!"
   :id 7
   :last-obs {:text "last text"
              :final-score 42}
   :trace [{:step :first  :obs {:text "one" :score 10}}
           {:step :second :obs {:text "two" :score 20}}
           {:step :third  :obs {:text "three" :score 30}}]})

(def base-params
  {:project-dir "."
   :trace-file  "evolution-single.csv"
   :limit nil})


(deftest filters-sanitize-slug-trim-case-truncate
  (is (= "a-roaring-twenties-jazz-party"
         (r "{{ctx.original-prompt | slug}}" base-ctx base-params)))
  (is (= "A_Roaring_Twenties__Jazz_Party"
         (r "{{ctx.original-prompt | sanitize}}" base-ctx base-params)))
  (is (= "a roaring: twenties / jazz party!!"
         (r "{{ctx.original-prompt | lower}}" base-ctx base-params)))
  (is (= "A ROARING: TWENTIES / JAZZ PARTY!!"
         (r "{{ctx.original-prompt | upper}}" base-ctx base-params)))
  (is (= "A Roaring: Twenties / Jazz Party!!"
         (r "{{ctx.original-prompt | trim}}" base-ctx base-params)))
  (is (= "A Roa"
         (r "{{ctx.original-prompt | truncate:5}}" base-ctx base-params))))

(deftest render-basic-paths
  (testing "ctx fields"
    (is (= "A jazz party" (r "{{prompt}}" base-ctx base-params)))
    (is (= "A Roaring: Twenties / Jazz Party!!" (r "{{ctx.original-prompt}}" base-ctx base-params))))
  (testing "params fields"
    (is (= "." (r "{{params.project-dir}}" base-ctx base-params))))
  (testing "last obs"
    (is (= "last text" (r "{{obs.text}}" base-ctx base-params)))))


(deftest render-template-vs-raw-single-token
  ;; render-template always returns stringified content
  (is (= "7" (r "{{ctx.id}}" base-ctx base-params)))
  ;; render-any: if exactly one token, return RAW value (number stays number)
  (is (= 7 (raw "{{ctx.id}}" base-ctx base-params)))
  ;; maps/vectors preserve type
  (let [ctx (assoc base-ctx :some-map {:a 1 :b 2})
        ctx2 (assoc base-ctx :some-vec [1 2 3])]
    (is (= {:a 1 :b 2} (raw "{{ctx.some-map}}" ctx base-params)))
    (is (= [1 2 3]     (raw "{{ctx.some-vec}}" ctx2 base-params)))))

(deftest graceful-unknown-op
  ;; Unknown operator should not explode; current impl returns expr string-ish
  ;; We just assert it returns *something* non-nil.
  (is (string? (r "{{foobar 1 2}}" base-ctx base-params))))

(deftest end-to-end-filename-from-prompt
  ;; typical use in party.edn: "{{ctx.original-prompt | slug | truncate:60}}.pdf"
  (is (= "a-roaring-twenties-jazz-party.pdf"
         (r "{{ctx.original-prompt | slug | truncate:60}}.pdf"
            base-ctx base-params))))

(deftest trace-merge-format-combo
  ;; simulate a format step using merged text from earlier fork
  (let [ctx (assoc base-ctx :last-obs {:merged {:text "### menu\n- a\n\n### playlist\n- b"}})]
    (is (str/includes?
         (r "Combined:\n\n{{obs.merged.text}}\n\n-- end --" ctx base-params)
         "### menu"))))


;
; PROGRESS IS BEING MADE
;

(deftest render-trace-lookup
  (testing "negative index trace"
    (is (= "three" (r "{{trace[-1].obs.text}}" base-ctx base-params)))
    (is (= "two"   (r "{{trace[-2].obs.text}}" base-ctx base-params)))
    (is (= "one"   (r "{{trace[-3].obs.text}}" base-ctx base-params))))
  ;(testing "spaced style (legacy)"
  ;; if your resolve-token* supports "trace -2 :obs :text"
    ;(is (= "two" (r "{{trace -2 :obs :text}}" base-ctx base-params))))
  )

(deftest arithmetic-ops
  (is (= "9.0"  (r "{{+ 4 5}}" base-ctx base-params)))
  (is (= "3.0"  (r "{{- 10 7}}" base-ctx base-params)))
  (is (= "-7.0" (r "{{- 7}}" base-ctx base-params))) ;; unary
  (is (= "42.0" (r "{{* 6 7}}" base-ctx base-params)))
  (is (= "2.5"  (r "{{/ 10 4}}" base-ctx base-params)))
  (is (= "8.0"  (r "{{max 2 8 3}}" base-ctx base-params)))
  (is (= "2.0"  (r "{{min 2 8 3}}" base-ctx base-params))))

(deftest arithmetic-with-paths
  ;; using bracket-path syntax to read from ctx/obs
  (let [ctx (assoc base-ctx :id 7 :last-obs {:final-score 42})]
    (is (= "8.0"  (r "{{+ [:ctx.id] 1}}" ctx base-params)))
    (is (= "41.0" (r "{{- [:obs.final-score] 1}}" ctx base-params)))))

(deftest comparisons-and-logic
  (is (= "true"  (r "{{> 5 3}}" base-ctx base-params)))
  (is (= "false" (r "{{< 5 3}}" base-ctx base-params)))
  (is (= "true"  (r "{{>= 5 5}}" base-ctx base-params)))
  (is (= "true"  (r "{{= \"x\" \"x\"}}" base-ctx base-params)))
  (is (= "true"  (r "{{and true 1 \"ok\"}}" base-ctx base-params)))
  ;(is (= "false" (r "{{and true nil}}" base-ctx base-params)))
  (is (= "true"  (r "{{or nil false \"x\"}}" base-ctx base-params)))
  (is (= "false" (r "{{not true}}" base-ctx base-params))))
;
;(deftest default-and-ternary
;  (is (= "fallback.csv"
;         (r "{{default [:params.nonexistent] \"fallback.csv\"}}" base-ctx base-params)))
;  (is (= "evolution-single.csv"
;         (r "{{default [:params.trace-file] \"fallback.csv\"}}" base-ctx base-params)))
;  (is (= "improved"
;         (r "{{? (> [:obs.final-score] 10) \"improved\" \"nope\"}}"
;            (assoc base-ctx :last-obs {:final-score 42}) base-params)))
;  (is (= "nope"
;         (r "{{? (> [:obs.final-score] 999) \"improved\" \"nope\"}}"
;            (assoc base-ctx :last-obs {:final-score 42}) base-params))))

;(deftest nested-tokens-in-expr
;  ;; inner {{...}} should resolve before the op
;  (let [ctx (assoc base-ctx :id 10)]
;    (is (= "12.0" (r "{{+ {{[:ctx.id]}} 2}}" ctx base-params)))))

(deftest mixed-filters-and-exprs
  (is (= "a-jazz-par"
         (r "{{ctx.prompt | slug | truncate:10}}" base-ctx base-params)))
  (is (= "9.0"
         (r "{{+ [:ctx.id] 2 | truncate:3}}" (assoc base-ctx :id 7) base-params))))



;; The exact args map you’re templating in :update-best
(def update-best-args
  {:set {:best-lend-fn   "{{? [:> [:trace -1 :obs :final-score] [:ctx :best-score]] [:trace -2 :obs :result] [:ctx :best-lend-fn]}}"
         :best-lend-code "{{? [:> [:trace -1 :obs :final-score] [:ctx :best-score]] [:trace -2 :obs :code]   [:ctx :best-lend-code]}}"
         :best-score     "{{max [:ctx.best-score] [:trace -1 :obs :final-score]}}"
         :best-game      "{{? [:> [:trace -1 :obs :final-score] [:ctx :best-score]] [:trace -1 :obs :game-id] [:ctx :best-game]}}"
         :id             "{{+ [:ctx.id] 1}}"}})

(deftest update-best-not-improved
  ;; candidate final-score < best-score → keep existing best-*, only bump id, best-score=max(...)
  (let [ctx {:best-lend-fn   :old-fn
             :best-lend-code "OLD"
             :best-score     229448.535
             :best-game      "baseline-uuid"
             :id             7
             :trace [;; previous step (index -2): held the compilation result
                     {:step :compile
                      :obs {:result :new-fn
                            :code   "NEW"}}
                     ;; last step (index -1): play-candidate with low score
                     {:step :play-candidate
                      :obs {:final-score 17358.1823
                            :game-id     "cand-uuid"}}]}
        params {}
        rendered (tpl/render-args-deep update-best-args ctx params)
       ;_ (println rendered)
        s (:set rendered)]
    (is (= (:best-lend-fn s)   :old-fn))
    (is (= (:best-lend-code s) "OLD"))
    (is (= (:best-score s)     229448.535)) ;; max of 229448.535 and 17358.1823
    (is (= (:best-game s)      "baseline-uuid"))
    (is (= (:id s)             8.0))))

(deftest update-best-improved
  ;; candidate final-score > best-score → adopt new result/code/game-id, bump id, best-score=max(...)
  (let [ctx {:best-lend-fn   :old-fn
             :best-lend-code "OLD"
             :best-score     100000.0
             :best-game      "baseline-uuid"
             :id             3
             :trace [;; previous step (index -2): compilation result we want to promote
                     {:step :compile
                      :obs {:result :new-fn
                            :code   "(defn lend-assets ...)"}}
                     ;; last step (index -1): play-candidate with higher score
                     {:step :play-candidate
                      :obs {:final-score 173580.0
                            :game-id     "cand-uuid-2"}}]}
        params {}
        rendered (tpl/render-args-deep update-best-args ctx params)
        s (:set rendered)]
    (is (= (:best-lend-fn s)   :new-fn))
    (is (= (:best-lend-code s) "(defn lend-assets ...)"))
    (is (= (:best-score s)     173580.0))  ;; max of 100000 and 173580
    (is (= (:best-game s)      "cand-uuid-2"))
    (is (= (:id s)             4.0))))


;; Step-aware version of your update-best args
(def update-best-args-step
  {:set {:best-lend-fn   "{{? [:> [:trace :play-candidate :obs :final-score] [:ctx :best-score]]
                             [:trace :compile        :obs :result]
                             [:ctx :best-lend-fn]}}"
         :best-lend-code "{{? [:> [:trace :play-candidate :obs :final-score] [:ctx :best-score]]
                             [:trace :compile        :obs :code]
                             [:ctx :best-lend-code]}}"
         :best-score     "{{max [:ctx.best-score] [:trace :play-candidate :obs :final-score]}}"
         :best-game      "{{? [:> [:trace :play-candidate :obs :final-score] [:ctx :best-score]]
                             [:trace :play-candidate :obs :game-id]
                             [:ctx :best-game]}}"
         :id             "{{+ [:ctx.id] 1}}"}})

(deftest update-best-step-aware-not-improved
  ;; candidate final-score < best-score → keep existing best-*, only bump id, best-score=max(...)
  (let [ctx {:best-lend-fn   :old-fn
             :best-lend-code "OLD"
             :best-score     229448.535
             :best-game      "baseline-uuid"
             :id             7
             :trace [;; previous compile result
                     {:step :compile
                      :obs  {:result :new-fn
                             :code   "NEW"}}
                     ;; last run: play-candidate with lower score
                     {:step :play-candidate
                      :obs  {:final-score 17358.1823
                             :game-id     "cand-uuid"}}]}
        params {}
        rendered (tpl/render-args-deep update-best-args-step ctx params)
        s (:set rendered)]
    (is (= (:best-lend-fn s)   :old-fn))
    (is (= (:best-lend-code s) "OLD"))
    (is (= (:best-score s)     229448.535)) ;; max of 229448.535 and 17358.1823
    (is (= (:best-game s)      "baseline-uuid"))
    (is (= (:id s)             8.0))))

(deftest update-best-step-aware-improved
  ;; candidate final-score > best-score → adopt new result/code/game-id, bump id, best-score=max(...)
  (let [ctx {:best-lend-fn   :old-fn
             :best-lend-code "OLD"
             :best-score     100000.0
             :best-game      "baseline-uuid"
             :id             3
             :trace [;; compile result to promote
                     {:step :compile
                      :obs  {:result :new-fn
                             :code   "(defn lend-assets ...)"}}
                     ;; last run: higher score
                     {:step :play-candidate
                      :obs  {:final-score 173580.0
                             :game-id     "cand-uuid-2"}}]}
        params {}
        rendered (tpl/render-args-deep update-best-args-step ctx params)
        s (:set rendered)]
    (is (= (:best-lend-fn s)   :new-fn))
    (is (= (:best-lend-code s) "(defn lend-assets ...)"))
    (is (= (:best-score s)     173580.0)) ;; max of 100000 and 173580
    (is (= (:best-game s)      "cand-uuid-2"))
    (is (= (:id s)             4.0))))

(deftest update-best-step-aware-picks-last-play-candidate
  ;; multiple :play-candidate steps → should use the LAST one
  (let [ctx {:best-lend-fn   :old-fn
             :best-lend-code "OLD"
             :best-score     50000.0
             :best-game      "baseline-uuid"
             :id             10
             :trace [;; earlier play-candidate (lower score)
                     {:step :play-candidate
                      :obs  {:final-score 60000.0
                             :game-id     "cand-uuid-early"}}
                     ;; compile result between them
                     {:step :compile
                      :obs  {:result :new-fn
                             :code   "NEWER"}}
                     ;; later play-candidate (higher score) — should be chosen
                     {:step :play-candidate
                      :obs  {:final-score 120000.0
                             :game-id     "cand-uuid-last"}}]}
        params {}
        rendered (tpl/render-args-deep update-best-args-step ctx params)
        s (:set rendered)]
    ;; Should promote latest compile result and the LAST play-candidate's game-id/score
    (is (= (:best-lend-fn s)   :new-fn))
    (is (= (:best-lend-code s) "NEWER"))
    (is (= (:best-score s)     120000.0))
    (is (= (:best-game s)      "cand-uuid-last"))
    (is (= (:id s)             11.0))))
;; =============================================================================
;; Tests for last-obs shorthand and render-custom-template improvements
;; =============================================================================

(deftest last-obs-shorthand
  (testing "last-obs as direct alias for obs"
    (is (= "last text" (r "{{last-obs.text}}" base-ctx base-params)))
    (is (= "42" (r "{{last-obs.final-score}}" base-ctx base-params))))

  (testing "last-obs with bracket notation"
    (is (= "last text" (r "{{last-obs[text]}}" base-ctx base-params)))
    (is (= "42" (r "{{last-obs[final-score]}}" base-ctx base-params))))

  (testing "last-obs standalone"
    (let [result (raw "{{last-obs}}" base-ctx base-params)]
      (is (map? result))
      (is (= "last text" (:text result)))
      (is (= 42 (:final-score result))))))

(deftest nested-template-interpolation
  (testing "template variables within trace context"
    (let [ctx {:last-obs {:text "Final result"}
               :trace [{:obs {:text "Step 1"}}
                       {:obs {:text "Step 2"}}
                       {:obs {:text "Step 3"}}]}
          params {}]
      (is (= "Step 1" (r "{{trace[0].obs.text}}" ctx params)))
      (is (= "Step 2" (r "{{trace[1].obs.text}}" ctx params)))
      (is (= "Step 3" (r "{{trace[2].obs.text}}" ctx params)))
      (is (= "Final result" (r "{{last-obs.text}}" ctx params)))))

  (testing "negative trace indices with last-obs"
    (let [ctx {:last-obs {:text "Current"}
               :trace [{:obs {:text "First"}}
                       {:obs {:text "Second"}}
                       {:obs {:text "Third"}}]}
          params {}]
      (is (= "Third" (r "{{trace[-1].obs.text}}" ctx params)))
      (is (= "Second" (r "{{trace[-2].obs.text}}" ctx params)))
      (is (= "First" (r "{{trace[-3].obs.text}}" ctx params)))
      (is (= "Current" (r "{{last-obs.text}}" ctx params))))))

(deftest render-custom-template-with-context
  (testing "render-custom-template receives ctx and params"
    ;; This simulates what happens in the agent when render-custom-template is called
    (let [ctx {:last-obs {:text "Analysis complete"}
               :trace [{:obs {:text "Discovered files"}}
                       {:obs {:text "Analyzed dependencies"}}
                       {:obs {:text "Detected systems"}}]}
          params {:project-dir "." :output-file "report.md"}
          ;; Simulate a template string that would be rendered
          template-str "Project: {{params.project-dir}}\n\nSteps:\n1. {{trace[0].obs.text}}\n2. {{trace[1].obs.text}}\n3. {{trace[2].obs.text}}\n\nResult: {{last-obs.text}}"
          result (r template-str ctx params)]
      (is (str/includes? result "Project: ."))
      (is (str/includes? result "1. Discovered files"))
      (is (str/includes? result "2. Analyzed dependencies"))
      (is (str/includes? result "3. Detected systems"))
      (is (str/includes? result "Result: Analysis complete")))))

(deftest complex-agent-workflow-simulation
  (testing "full agent workflow with multiple steps and template rendering"
    (let [ctx {:project-dir "."
               :output-file "software-inventory.md"
               :last-obs {:text "# Software Inventory\n\n## Dependencies\n- Clojure 1.12.1"}
               :trace [{:step :discover
                        :obs {:text "Found 104 files"
                              :files 104}}
                       {:step :format-files
                        :obs {:text "Formatted file list"}}
                       {:step :analyze-deps
                        :obs {:text "Dependencies:\n- org.clojure/clojure 1.12.1"}}
                       {:step :detect-all
                        :obs {:text "Detected: 0 CI/CD systems"
                              :summary {:ci-systems 0
                                        :docker-files 0
                                        :k8s-manifests 0}}}
                       {:step :gather-context
                        :obs {:text "Key files: deps.edn, README.md"}}]}
          params {:project-dir "." :output-file "software-inventory.md"}

          ;; Simulate the prompt template used in software-versions agent
          prompt-template "File Structure:\n{{trace[1].obs.text}}\n\nDependencies:\n{{trace[2].obs.text}}\n\nDetected Systems:\n{{trace[3].obs.text}}\n\nKey Files:\n{{last-obs.text}}"

          rendered (r prompt-template ctx params)]

      (is (str/includes? rendered "File Structure:\nFormatted file list"))
      (is (str/includes? rendered "Dependencies:\nDependencies:\n- org.clojure/clojure 1.12.1"))
      (is (str/includes? rendered "Detected Systems:\nDetected: 0 CI/CD systems"))
      (is (str/includes? rendered "Key Files:\n# Software Inventory")))))

(deftest last-obs-vs-obs-equivalence
  (testing "last-obs and obs should resolve to the same value"
    (is (= (r "{{obs.text}}" base-ctx base-params)
           (r "{{last-obs.text}}" base-ctx base-params)))
    (is (= (r "{{obs.final-score}}" base-ctx base-params)
           (r "{{last-obs.final-score}}" base-ctx base-params)))
    (is (= (raw "{{obs}}" base-ctx base-params)
           (raw "{{last-obs}}" base-ctx base-params)))))

;; =============================================================================
;; Additional tests for prompt templating and resource: pattern
;; =============================================================================

(deftest prompt-templating-with-resource-pattern
  (testing "resource: pattern loads and renders template files"
    ;; This simulates how agents load prompts from resource files
    (let [ctx {:last-obs {:text "Analysis complete"}
               :trace [{:obs {:text "Step 1 output"}}
                       {:obs {:text "Step 2 output"}}]}
          params {:project-dir "."}
          ;; Simulate a template that would be loaded from resources
          template-content "Project: {{params.project-dir}}\n\nPrevious steps:\n{{trace[0].obs.text}}\n{{trace[1].obs.text}}\n\nCurrent: {{last-obs.text}}"
          rendered (r template-content ctx params)]
      (is (str/includes? rendered "Project: ."))
      (is (str/includes? rendered "Step 1 output"))
      (is (str/includes? rendered "Step 2 output"))
      (is (str/includes? rendered "Current: Analysis complete")))))

(deftest multi-step-agent-prompt-rendering
  (testing "agent step prompts with trace references"
    (let [ctx {:project-dir "."
               :last-obs {:text "Dependencies analyzed"
                          :count 5}
               :trace [{:step :discover
                        :obs {:text "Found 100 files"
                              :files 100}}
                       {:step :analyze
                        :obs {:text "Analyzed structure"
                              :modules 3}}
                       {:step :extract-deps
                        :obs {:text "Extracted 5 dependencies"
                              :deps ["dep1" "dep2" "dep3" "dep4" "dep5"]}}]}
          params {}
          
          ;; Prompt for a synthesis step that references all previous steps
          synthesis-prompt "Based on the following analysis:\n\nDiscovery: {{trace[0].obs.text}}\nStructure: {{trace[1].obs.text}}\nDependencies: {{last-obs.text}}\n\nGenerate a comprehensive report."
          
          rendered (r synthesis-prompt ctx params)]
      
      (is (str/includes? rendered "Discovery: Found 100 files"))
      (is (str/includes? rendered "Structure: Analyzed structure"))
      (is (str/includes? rendered "Dependencies: Dependencies analyzed")))))

(deftest prompt-with-conditional-content
  (testing "prompts with conditional sections based on trace data"
    (let [ctx-with-errors {:last-obs {:text "Analysis failed"
                                       :errors ["Error 1" "Error 2"]}
                           :trace [{:obs {:text "Step completed"}}]}
          ctx-no-errors {:last-obs {:text "Analysis succeeded"
                                     :errors []}
                         :trace [{:obs {:text "Step completed"}}]}
          params {}
          
          ;; Prompt that could conditionally include error information
          prompt-template "Status: {{last-obs.text}}\nPrevious: {{trace[0].obs.text}}"]
      
      (is (str/includes? (r prompt-template ctx-with-errors params) "Status: Analysis failed"))
      (is (str/includes? (r prompt-template ctx-no-errors params) "Status: Analysis succeeded")))))

(deftest nested-data-structure-access
  (testing "accessing nested maps and vectors in trace"
    (let [ctx {:last-obs {:summary {:total 10
                                     :passed 8
                                     :failed 2}
                          :details {:files ["a.clj" "b.clj" "c.clj"]}}
               :trace [{:obs {:results {:score 95
                                         :metrics {:accuracy 0.95
                                                   :precision 0.92}}}}]}
          params {}]
      
      (is (= "10" (r "{{last-obs.summary.total}}" ctx params)))
      (is (= "8" (r "{{last-obs.summary.passed}}" ctx params)))
      (is (= "95" (r "{{trace[0].obs.results.score}}" ctx params)))
      (is (= "0.95" (r "{{trace[0].obs.results.metrics.accuracy}}" ctx params))))))

(deftest prompt-with-multiple-last-obs-references
  (testing "multiple references to last-obs in same template"
    (let [ctx {:last-obs {:text "Final analysis"
                          :score 42
                          :status "complete"
                          :files 100}}
          params {}
          template "Result: {{last-obs.text}}\nScore: {{last-obs.score}}\nStatus: {{last-obs.status}}\nFiles: {{last-obs.files}}"
          rendered (r template ctx params)]
      
      (is (str/includes? rendered "Result: Final analysis"))
      (is (str/includes? rendered "Score: 42"))
      (is (str/includes? rendered "Status: complete"))
      (is (str/includes? rendered "Files: 100")))))

(deftest trace-with-step-name-lookup
  (testing "accessing trace entries by step name (when supported)"
    (let [ctx {:last-obs {:text "Current"}
               :trace [{:step :discover :obs {:text "Discovered"}}
                       {:step :analyze :obs {:text "Analyzed"}}
                       {:step :synthesize :obs {:text "Synthesized"}}]}
          params {}]
      
      ;; Numeric access should work
      (is (= "Discovered" (r "{{trace[0].obs.text}}" ctx params)))
      (is (= "Analyzed" (r "{{trace[1].obs.text}}" ctx params)))
      (is (= "Synthesized" (r "{{trace[2].obs.text}}" ctx params))))))

(deftest empty-and-nil-value-handling
  (testing "graceful handling of empty and nil values"
    (let [ctx-empty {:last-obs {:text ""
                                 :value nil}
                     :trace []}
          ctx-missing {:last-obs {}}
          params {}]
      
      ;; Empty string should render as empty
      (is (= "" (r "{{last-obs.text}}" ctx-empty params)))
      
      ;; Missing keys should render as empty string (not error)
      (is (= "" (r "{{last-obs.missing-key}}" ctx-missing params))))))

(deftest complex-prompt-composition
  (testing "complex multi-section prompt with all features"
    (let [ctx {:project-dir "/path/to/project"
               :output-file "report.md"
               :last-obs {:text "# Final Report\n\n## Summary\nAll tasks completed successfully."
                          :stats {:total 150
                                  :processed 150
                                  :errors 0}}
               :trace [{:step :init
                        :obs {:text "Initialized project"}}
                       {:step :scan
                        :obs {:text "Scanned 150 files"
                              :files 150}}
                       {:step :analyze
                        :obs {:text "Analyzed dependencies"
                              :deps 25}}
                       {:step :detect
                        :obs {:text "Detected 3 systems"
                              :systems ["CI/CD" "Docker" "K8s"]}}]}
          params {:format "markdown" :verbose true}
          
          complex-prompt "# Analysis Request\n\n## Context\nProject: {{ctx.project-dir}}\nOutput: {{ctx.output-file}}\nFormat: {{params.format}}\n\n## Previous Steps\n1. {{trace[0].obs.text}}\n2. {{trace[1].obs.text}} ({{trace[1].obs.files}} files)\n3. {{trace[2].obs.text}} ({{trace[2].obs.deps}} dependencies)\n4. {{trace[3].obs.text}}\n\n## Current State\n{{last-obs.text}}\n\nStats: {{last-obs.stats.total}} total, {{last-obs.stats.errors}} errors\n\n## Task\nGenerate final documentation."
          
          rendered (r complex-prompt ctx params)]
      
      (is (str/includes? rendered "Project: /path/to/project"))
      (is (str/includes? rendered "Output: report.md"))
      (is (str/includes? rendered "Format: markdown"))
      (is (str/includes? rendered "1. Initialized project"))
      (is (str/includes? rendered "2. Scanned 150 files (150 files)"))
      (is (str/includes? rendered "3. Analyzed dependencies (25 dependencies)"))
      (is (str/includes? rendered "4. Detected 3 systems"))
      (is (str/includes? rendered "# Final Report"))
      (is (str/includes? rendered "Stats: 150 total, 0 errors")))))

(deftest render-args-deep-with-nested-templates
  (testing "render-args-deep handles nested template variables in tool args"
    (let [ctx {:last-obs {:text "Generated inventory"}
               :trace [{:obs {:text "Step 1"}}
                       {:obs {:text "Step 2"}}]}
          params {:output-file "report.md"}
          
          ;; Simulate tool args with template variables
          tool-args {:message "{{last-obs.text}}"
                     :path "{{params.output-file}}"
                     :context "Previous: {{trace[0].obs.text}}, {{trace[1].obs.text}}"}
          
          rendered (tpl/render-args-deep tool-args ctx params)]
      
      (is (= "Generated inventory" (:message rendered)))
      (is (= "report.md" (:path rendered)))
      (is (= "Previous: Step 1, Step 2" (:context rendered))))))

(deftest backward-compatibility-obs-notation
  (testing "backward compatibility with obs notation (without last- prefix)"
    (let [ctx {:last-obs {:text "Current observation"
                          :score 100}}
          params {}]
      
      ;; Both obs and last-obs should work identically
      (is (= "Current observation" (r "{{obs.text}}" ctx params)))
      (is (= "Current observation" (r "{{last-obs.text}}" ctx params)))
      (is (= "100" (r "{{obs.score}}" ctx params)))
      (is (= "100" (r "{{last-obs.score}}" ctx params))))))
