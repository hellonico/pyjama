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
         (r "{{+ [:ctx.id] 2 | truncate:3}}" (assoc base-ctx :id 7) base-params)))
  )



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
                           :code   "(defn lend-assets ...)" }}
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