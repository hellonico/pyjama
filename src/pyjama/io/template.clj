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

;; --- filters ---------------------------------------------------------------

(defn- parse-filter [s]
 ;; "truncate:50"     -> ["truncate" ["50"]]
 ;; "slug"            -> ["slug"      []]
 ;; "sanitize,lower"  -> ["sanitize"  ["lower"]]   ; (rare, but we support comma args)
 (let [s       (clojure.string/trim (str s))
       ;; split on the FIRST ":" only, so arg strings can contain colons
       parts   (clojure.string/split s #":" 2)
       name    (clojure.string/trim (first parts))
       arg-str (second parts)
       args    (if (seq arg-str)
                (->> (clojure.string/split arg-str #",")
                     (map clojure.string/trim)
                     (remove clojure.string/blank?)
                     vec)
                [])]
  [name args]))

(defn- apply-filter [v [fname args]]
 (case fname
  ;; filename-safe-ish slug
  "slug"     (-> (str v)
                 (str/lower-case)
                 (str/replace #"[^\p{Alnum}]+" "-")
                 (str/replace #"^-+|-+$" "")
                 (str/replace #"-{2,}" "-"))
  ;; keep letters, digits, _ and -
  "sanitize" (-> (str v)
                 (str/replace #"\s+" "_")
                 (str/replace #"[^A-Za-z0-9_\-]" ""))
  "lower"    (some-> v str str/lower-case)
  "upper"    (some-> v str str/upper-case)
  "trim"     (some-> v str str/trim)
  "truncate" (let [n (some-> (first args) Integer/parseInt)
                   s (str v)]
              (if (and n (> (count s) n))
               (subs s 0 n)
               s))
  ;; default: unknown filter = no-op
  v))

(defn- apply-filters [v filters]
 (reduce apply-filter v filters))

(defn- resolve-with-filters [ctx params token]
 ;; token looks like: "ctx.original-prompt | slug | truncate:60"
 (let [parts   (map str/trim (str/split token #"\|"))
       expr    (first parts)
       filters (map parse-filter (rest parts))
       raw     (resolve-token* ctx params expr)]
  (apply-filters raw filters)))

;; --- rendering -------------------------------------------------------------

(defn render-template
 "Render a STRING by replacing all {{...}} with stringified values."
 [tpl ctx params]
 (str/replace tpl token-re
              (fn [[_ t]]
               (let [v (resolve-with-filters ctx params t)]
                (cond
                 (nil? v)   ""
                 (string? v) v
                 :else      (pr-str v))))))

(defn- render-any
 "If s is exactly one {{token}}, return the RAW VALUE (after filters).
  Otherwise, treat as templated string and return a STRING."
 [s ctx params]
 (if-let [[_ expr] (re-matches single-token-re s)]
  (resolve-with-filters ctx params expr)
  (render-template s ctx params)))

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
