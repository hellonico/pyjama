(ns pyjama.tools.extract
  (:require [clojure.string :as str]))

(defn extract-filenames
  "Extract filenames mentioned in text.
   Looks for common file extensions and paths."
  [{:keys [text]}]
  (let [;; Regex to capture likely file paths.
        ;; Matches:
        ;; - Optional leading / or ./ or ../
        ;; - alphanumeric chars, underscores, dashes, dots
        ;; - Must have a file extension (e.g. .clj, .md, .js)
        ;; - Length between 3 and 100 chars
        path-pattern #"(?:\.\.?\/)?(?:[a-zA-Z0-9_\-\.]+\/)*[a-zA-Z0-9_\-\.]+\.[a-zA-Z0-9]{1,10}"

        matches (->> (re-seq path-pattern text)
                     (map str/trim)
                     (map #(if (str/starts-with? % "/") (subs % 1) %))
                     (remove #(or (= % ".")
                                  (= % "..")
                                  (= % "...")))
                     distinct
                     vec)]

    {:status :ok
     :files matches
     :count (count matches)
     :text (str "Extracted " (count matches) " files:\n" (str/join "\n" matches))}))
