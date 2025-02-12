(ns pyjama.utils
  (:require [clojure.java.io :as io]
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