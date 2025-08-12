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
  ;  ;; if your resolve-token* supports "trace -2 :obs :text"
  ;  (is (= "two" (r "{{trace -2 :obs :text}}" base-ctx base-params))))
  )

(deftest arithmetic-ops
  (is (= "9.0"  (r "{{+ 4 5}}" base-ctx base-params)))
  (is (= "3.0"  (r "{{- 10 7}}" base-ctx base-params)))
  (is (= "-7.0" (r "{{- 7}}" base-ctx base-params))) ;; unary
  (is (= "42.0" (r "{{* 6 7}}" base-ctx base-params)))
  (is (= "2.5"  (r "{{/ 10 4}}" base-ctx base-params)))
  (is (= "8.0"  (r "{{max 2 8 3}}" base-ctx base-params)))
  (is (= "2.0"  (r "{{min 2 8 3}}" base-ctx base-params))))

;(deftest arithmetic-with-paths
;  ;; using bracket-path syntax to read from ctx/obs
;  (let [ctx (assoc base-ctx :id 7 :last-obs {:final-score 42})]
;    (is (= "8.0"  (r "{{+ [:ctx.id] 1}}" ctx base-params)))
;    (is (= "41.0" (r "{{- [:obs.final-score] 1}}" ctx base-params)))))

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
  ;(is (= "9.0"
  ;       (r "{{+ [:ctx.id] 2 | truncate:3}}" (assoc base-ctx :id 7) base-params)))
  )

