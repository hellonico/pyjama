(ns pyjama.ollama.cli
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str])
  (:import (java.io BufferedInputStream File FileOutputStream)
           (java.net URL)))

(defn file-exists? [filename]
  (.exists (File. filename)))

(defn download-with-progress [url filename]
  (if (file-exists? filename)
    (println (str filename " already exists. Skipping download."))
    (let [connection (.openConnection (URL. url))
          content-length (.getContentLengthLong connection)
          input-stream (BufferedInputStream. (.getInputStream connection))
          output-stream (FileOutputStream. filename)
          buffer (byte-array 1024)
          spinner (vec ["-" "\\" "|" "/"])]  ;; Spinner for unknown file size
      (println (str "Downloading " filename (if (> content-length 0)
                                              (str " (" (/ content-length 1024 1024.0) " MB)...")
                                              "...")))
      (loop [total-bytes 0 spinner-index 0]
        (let [bytes-read (.read input-stream buffer)]
          (if (pos? bytes-read)
            (do
              (.write output-stream buffer 0 bytes-read)
              (if (> content-length 0)
                (let [progress (* 100.0 (/ total-bytes content-length))]
                  (printf "\rProgress: %.2f%%" progress))
                (printf "\rDownloading... %s" (nth spinner (mod spinner-index 4))))
              (flush)
              (recur (+ total-bytes bytes-read) (inc spinner-index)))
            (do
              (println "\nDownload complete!")
              (.close input-stream)
              (.close output-stream))))))))

(defn create-modelfile [filename]
  (spit "Modelfile" (str "FROM " filename))
  (println "Modelfile created."))

(defn run-command [cmd]
  (let [{:keys [exit out err]} (apply sh (str/split cmd #"\s+"))]
    (println out)
    (when (not= exit 0) (println "Error:" err))))

(defn delete-file [filename]
  (let [file (File. filename)]
    (when (.exists file)
      (.delete file)
      (println (str "Deleted " filename)))))

(defn create-model
  "perform the steps to upload a model as is on ollama"
  [hugging-face-url ollama-profile]
  (let [url hugging-face-url
        filename (last (str/split url #"/"))]
    (download-with-progress url filename)
    (create-modelfile filename)
    (run-command (str "ollama create " ollama-profile "/" filename))
    (run-command (str "ollama push " ollama-profile "/" filename))

    (delete-file filename)
    (delete-file "Modelfile")

    (println "Your model is up:
      https://ollama.com/" ollama-profile "/" filename
             "\n""")))

(defn -main [& args]
  (create-model
    (first args)
    (second args)))