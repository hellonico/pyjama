(ns pyjama.io.template
 (:require [clojure.string :as str]))

(def token-re #"\{\{([^}]+)\}\}")

(defn- kwish [s]
 (cond
  (keyword? s) s
  (and (string? s) (str/starts-with? s ":")) (keyword (subs s 1))
  :else s))

(defn- parse-bracket-path [s]
 (let [s (str/replace s #"\]" "")
       parts (-> s (str/replace #"\[" ".") (str/split #"\."))]
  (->> parts
       (remove str/blank?)
       (map (fn [p]
             (cond
              (re-matches #"-?\d+" p) (Integer/parseInt p)
              (str/starts-with? p ":") (keyword (subs p 1))
              :else p))))))

(defn- resolve-trace [ctx idx ks]
 (let [tr (:trace ctx)
       n  (count tr)
       i  (if (neg? idx) (+ n idx) idx)
       itm (nth tr i nil)]
  (when itm (get-in itm (mapv kwish ks)))))

(defn- kwify [x]
 (cond
  (keyword? x) x
  (string? x)  (keyword x)
  :else        x))

(defn- resolve-token [ctx params token]
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
     ;; fallback into ctx using keywordized path
     (get-in ctx (map kwify parts))))

   ;; spaced style: "trace -2 :obs :text"   (already keywords, ok)
   (str/starts-with? content "trace ")
   (let [[_ idx & ks] (str/split content #"\s+")]
    (resolve-trace ctx (Integer/parseInt idx) (map kwify ks)))

   ;; simple fallbacks...
   (= content "obs")    (:last-obs ctx)
   (= content "prompt") (:prompt ctx)
   (= content "params") params

   (str/starts-with? content ":")
   (get-in ctx (into [:last-obs]
                     (map (comp kwify #(subs % 1))
                          (str/split content #"\s+"))))

   :else (get ctx (keyword content)))))

(defn render-template [tpl ctx params]
 (str/replace tpl token-re
              (fn [[_ t]]
               (let [v (resolve-token ctx params t)]
                (cond
                 (nil? v) ""
                 (string? v) v
                 :else (pr-str v))))))
