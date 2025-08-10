(ns pyjama.io.template
 (:require [clojure.string :as str]
           [clojure.walk :as walk]))

;; ----------------------------------------------------------------------------
;; Tokens & helpers
;; ----------------------------------------------------------------------------

(def token-re #"\{\{([^}]+)\}\}")
(def single-token-re #"^\s*\{\{([^}]+)\}\}\s*$")

(defn- kwish [s]
 (cond
  (keyword? s) s
  (and (string? s) (str/starts-with? s ":")) (keyword (subs s 1))
  :else s))

(defn- kwify [x]
 (cond
  (keyword? x) x
  (string? x)  (keyword x)
  :else        x))

(defn- parse-bracket-path
 "Turn e.g. trace[-2].obs.files  OR  trace[-2][:obs][:files]
  into a seq of segments: [\"trace\" -2 :obs :files]"
 [s]
 (let [s (-> s (str/replace #"\]" "") (str/replace #"\[" "."))
       parts (->> (str/split s #"\.")
                  (remove str/blank?))]
  (map (fn [p]
        (cond
         (re-matches #"-?\d+" p) (Integer/parseInt p)
         (str/starts-with? p ":") (keyword (subs p 1))
         :else p))
       parts)))

(defn- resolve-trace [ctx idx ks]
 (let [tr (:trace ctx)
       n  (count tr)
       i  (if (neg? idx) (+ n idx) idx)
       itm (when (and (>= i 0) (< i n)) (nth tr i nil))]
  (when itm (get-in itm (mapv kwish ks)))))

;; ----------------------------------------------------------------------------
;; Core resolver
;; ----------------------------------------------------------------------------

(defn- resolve-token* [ctx params token]
 (let [content (str/trim token)]
  (cond
   ;; dot/bracket style
   (re-find #"\[|\." content)
   (let [parts (parse-bracket-path content)]
    (case (first parts)
     "obs"    (let [ks (map kwify (rest parts))]
               (get-in ctx (into [:last-obs] ks)))
     "prompt" (:prompt ctx)
     "params" (get-in params (map kwify (rest parts)))
     "trace"  (let [idx (second parts)
                    ks  (map kwify (drop 2 parts))]
               (resolve-trace ctx idx ks))
     "ctx"    (get-in ctx (map kwify (rest parts)))
     ;; fallback into ctx using keywordized path
     (get-in ctx (map kwify parts))))

   ;; spaced style: "trace -2 :obs :text"
   (str/starts-with? content "trace ")
   (let [[_ idx & ks] (str/split content #"\s+")]
    (resolve-trace ctx (Integer/parseInt idx) (map kwify ks)))

   ;; simple whole-token shortcuts
   (= content "obs")    (:last-obs ctx)
   (= content "prompt") (:prompt ctx)
   (= content "params") params
   (= content "ctx")    ctx

   ;; ":obs :text" spaced keywords, read under last-obs
   (str/starts-with? content ":")
   (get-in ctx (into [:last-obs]
                     (map (comp kwify #(subs % 1))
                          (str/split content #"\s+"))))

   ;; fallback: treat as key in ctx
   :else (get ctx (keyword content)))))

;; ----------------------------------------------------------------------------
;; String-mode vs value-mode rendering
;; ----------------------------------------------------------------------------

(defn render-template
 "Render a STRING by replacing all {{...}} with stringified values.
  Use this for prompts/messages."
 [tpl ctx params]
 (str/replace tpl token-re
              (fn [[_ t]]
               (let [v (resolve-token* ctx params t)]
                (cond
                 (nil? v)   ""
                 (string? v) v
                 :else      (pr-str v))))))

(defn- render-any
 "If s is exactly one {{token}}, return the RAW VALUE.
  Otherwise, treat as a templated string and return a STRING."
 [s ctx params]
 (if-let [[_ expr] (re-matches single-token-re s)]
  (resolve-token* ctx params expr)           ;; value (vector/map/number/string/nil)
  (render-template s ctx params))            ;; string

 )

(defn render-value
 "Value-aware renderer: for strings with tokens, return raw value for single-token,
  else a rendered string. Non-strings pass through."
 [v ctx params]
 (if (and (string? v) (re-find token-re v))
  (render-any v ctx params)
  v))

(defn render-args-deep
 "Deeply render a map intended for tool :args. Single-token strings
  become their resolved raw values; multi-token become strings."
 [m ctx params]
 (walk/postwalk (fn [x] (render-value x ctx params)) (or m {})))
