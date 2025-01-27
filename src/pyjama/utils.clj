(ns pyjama.utils)

(defn to-markdown [{:keys [headers rows]}]
  (let [header-row (str "| " (clojure.string/join " | " (map name headers)) " |")
        separator-row (str "| " (clojure.string/join " | " (repeat (count headers) "---")) " |")
        data-rows (map (fn [row]
                         (str "| " (clojure.string/join " | " (map #(get row % "") headers)) " |"))
                       rows)]
    (clojure.string/join "\n" (concat [header-row separator-row] data-rows))))