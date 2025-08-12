(ns pyjama.tools.pandoc
 (:require [clojure.java.shell :as shell]
           [clojure.java.io :as io]))

(defn md->pdf
 "Convert a Markdown file to PDF using Pandoc.
  Args:
    :input  - path to input .md
    :output - path to output .pdf
  Returns:
    {:status :ok :pdf <output-path>} or {:status :error :message <stderr>}"
 [{:keys [input output]}]
 (if (and input output (.exists (io/file input)))
  (let [{:keys [exit err]} (shell/sh "pandoc" input "-o" output)]
   (if (zero? exit)
    {:status :ok :pdf (.getAbsolutePath (io/file output))}
    {:status :error :message err}))
  {:status :error :message (str "Missing input/output or file not found: " input)}))
