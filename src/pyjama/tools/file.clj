(ns pyjama.tools.file
 (:require [clojure.java.io :as io]
           [clojure.string :as str])
 (:import (java.time ZonedDateTime ZoneId)
          (java.time.format DateTimeFormatter)
          (java.io File)))

(def ^:private ts-formatter
 (DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss"))

(defn- now-ts []
 (.format (ZonedDateTime/now (ZoneId/systemDefault)) ts-formatter))

(defn- sanitize-filename [s]
 (-> (or s "output")
     (str/replace #"[^\p{Alnum}\.\-]+" "_")
     (str/replace #"_{2,}" "_")
     (str/replace #"^_|_$" "")))

(defn- ensure-parent-dirs! [^File f]
 (when-let [p (.getParentFile f)]
  (.mkdirs p))
 f)

(defn normalize-spaces [s]
 ;; Replace narrow no-break space (\u202F) and no-break space (\u00A0) with regular space
 (-> s
     (clojure.string/replace #"\u202F" " ")
     (clojure.string/replace #"\u00A0" " ")))

(defn write-file
 "Tool: write the incoming :message to a file.

  Args map (merged from EDN :args + runtime):
    :message   - string to write (defaults to last LLM text via runner)
    :path      - full file path (wins if provided)
    :dir       - directory (default \"out/\")
    :name      - filename (if omitted, derived from first words or timestamp)
    :append    - boolean (default false)
    :encoding  - default \"UTF-8\"

  Returns observation like:
    {:status :ok :file \"/abs/path\" :bytes 1234 :append? false}"
 [{:keys [message path dir name append encoding] :as _args}]
 (let [encoding (or encoding "UTF-8")
       dir (or dir "out")
       name (or (some-> message (subs 0 (min 40 (count message)))
                        (str/replace #"\s+" "_")
                        sanitize-filename
                        (str ".md"))
                (str "summary-" (now-ts) ".md"))
       f (io/file (or path (str dir File/separator name)))]
  (ensure-parent-dirs! f)
  (let [clean-message (normalize-spaces (or message ""))]
   (spit f clean-message :append (boolean append) :encoding encoding))
  {:status  :ok
   :file    (.getAbsolutePath f)
   :append? (boolean append)
   :bytes   (.length f)}))