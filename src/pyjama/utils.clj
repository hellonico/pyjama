(ns pyjama.utils
 (:require [clojure.java.io :as io]
           [cheshire.core :as json]
           [clojure.string :as str]))

(defn to-markdown [{:keys [headers rows]}]
 (let [header-row (str "| " (clojure.string/join " | " (map name headers)) " |")
       separator-row (str "| " (clojure.string/join " | " (repeat (count headers) "---")) " |")
       data-rows (map (fn [row]
                       (str "| " (clojure.string/join " | " (map #(get row % "") headers)) " |"))
                      rows)]
  (clojure.string/join "\n" (concat [header-row separator-row] data-rows))))

(defn load-lines-of-file
 "Load all the lines of a text file.
 Can specify start and end.
 Also lines with # are not taken into account"
 ([file-path]
  (load-lines-of-file file-path 0 ##Inf))

 ([file-path start]
  (load-lines-of-file file-path start ##Inf))

 ([file-path start end]
  (with-open [rdr (io/reader file-path)]
   (->> (line-seq rdr)
        (drop start)
        (take (if (= end ##Inf) Integer/MAX_VALUE (- end start)))
        (filter #(not (str/starts-with? (str %) "#")))
        (into [])
        doall))))

(defn load-pre
 [path-or-pre]
 (try
  (if-let [resource (clojure.java.io/resource path-or-pre) ]
   (slurp resource)
   path-or-pre)
  (catch Exception _
   path-or-pre)))

; moved from core
(defn templated-prompt [input]
 (if (contains? input :pre)
  (let [pre (:pre input)
        prompt (:prompt input)
        template (if (vector? pre) (first pre) (load-pre pre))
        args (concat (if (vector? pre) (rest pre) [])
                     (if (vector? prompt) prompt [prompt]))
        prompt (merge (dissoc input :pre) {:prompt (apply format template args)})
        ]
   prompt
   )
  input))

(defn parse-json-or-text [s]
 (try
  (json/parse-string s true)  ;; `true` converts keys to keywords
  (catch Exception _ s)))
