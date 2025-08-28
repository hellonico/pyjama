(ns pyjama.tools.file
 (:require [clojure.java.io :as io]
           [clojure.string :as str]
           [pyjama.helpers.file :as hf]
           )
 (:import (java.time ZonedDateTime ZoneId)
          (java.time.format DateTimeFormatter)
          (java.io File)))

(def ^:private ts-formatter
 (DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss"))

(defn- now-ts []
 (.format (ZonedDateTime/now (ZoneId/systemDefault)) ts-formatter))

(defn normalize-spaces [s]
 ;; Replace narrow no-break space (\u202F) and no-break space (\u00A0) with regular space
 (-> s
     (clojure.string/replace #"\u202F" " ")
     (clojure.string/replace #"\u00A0" " ")))

(defn write-file
 [{:keys [message path dir name append encoding] :as _args}]
 (let [encoding (or encoding "UTF-8")
       dir      (or dir "out")
       name     (or name
                    (some-> message
                            (subs 0 (min 40 (count message)))
                            (str/replace #"\s+" "_")
                            hf/sanitize-filename
                            (str ".md"))
                    (str "summary-" (now-ts) ".md"))
       ;; optional: also sanitize provided name
       name     (hf/sanitize-filename name)
       f        (io/file (or path (str dir File/separator name)))]
  (hf/ensure-parent-dirs! f)
  (let [clean-message (normalize-spaces (or message ""))]
   (spit f clean-message :append (boolean append) :encoding encoding))
  {:status  :ok
   :file    (.getAbsolutePath f)
   :append? (boolean append)
   :bytes   (.length f)}))