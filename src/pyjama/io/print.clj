(ns pyjama.io.print
  (:require [clojure.string :as str]))
;
;(defn print-table [m]
;  (doseq [[k v] m]
;    (println (format "| %-15s | %-100s |" k v))))
;
;(defn print-nested-map [k v]
;  (println (format "| %-15s | %-100s |" k ""))
;  (doseq [[sub-k sub-v] v]
;    (println (format "| %-15s | %-100s |" (str " - " sub-k) sub-v))))
;
;(defn pretty-print-map [m]
;  (doseq [[k v] m]
;    (if (map? v)
;      (print-nested-map k v)
;      (println (format "| %-15s | %-100s |" k v)))))

(def max-length 110)

(defn truncate-text [text]
  (if (> (count text) max-length)
    (str (subs text 0 max-length) "...")
    text))

(defn remove-newlines [text]
  (str/replace text #"\n" " ")) ; Replaces all newlines with a space

(defn print-table [m]
  (doseq [[k v] m]
    (println (format "| %-20s | %-120s |" k v))))

(defn print-nested-map [k v]
  (println (format "| %-20s | %-100s |" k ""))
  (doseq [[sub-k sub-v] v]
    (println (format "| %-20s | %-120s |" (str " - " sub-k) (truncate-text (remove-newlines (str sub-v)))))))

(defn pretty-print-map [m]
  (doseq [[k v] m]
    (if (map? v)
      (print-nested-map k v)
      (println (format "| %-20s | %-120s |" k (truncate-text (remove-newlines (str v))))))))

(defn print-markdown-table [keys data]
  (let [trim-str #(-> % str clojure.string/trim)
        rows (map #(map (comp trim-str %) keys) data)
        headers (map name keys)
        col-widths (map (fn [col] (apply max (count (name col)) (map #(count (trim-str %)) (map col data)))) keys)
        format-row (fn [row] (str "| " (clojure.string/join " | " (map #(format (str "%-" % "s") (trim-str %2)) col-widths row)) " |"))
        separator (str "|-" (clojure.string/join "-|-" (map #(apply str (repeat % "-")) col-widths)) "-|")]
    (println (format-row headers))
    (println separator)
    (doseq [row rows] (println (format-row row)))))
(def print-table print-markdown-table)