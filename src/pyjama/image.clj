(ns pyjama.image
  (:require [clojure.java.io :as io])
  (:import (java.util Base64)))

(defn image-to-base64 [image-path]
  (try
    (let [^bytes file-bytes (with-open [input-stream (io/input-stream image-path)]
                       (let [buffer (byte-array (.available input-stream))]
                         (.read input-stream buffer)
                         buffer))
          base64-bytes (.encode (Base64/getEncoder) file-bytes)
          ^String encoding "UTF-8"
          ]
      (String. base64-bytes ^String encoding))
    (catch Exception e
      (println "Error converting image to Base64:" (.getMessage e))
      nil)))