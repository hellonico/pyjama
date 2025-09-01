(ns pyjama.image
  "Utilities for converting images to/from Base64 representations.

  The functions are intentionally pure – they only manipulate data in memory
  and do not perform any I/O beyond reading the source image file and
  optionally writing the decoded image back to disk.

  All functions return `nil` when an error occurs.  For callers that need
  more detailed error information, consider using `try-image-to-base64`
  instead."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import (java.nio.file Files Path Paths)
           (java.util Base64)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- read-file-bytes
  "Read the entire contents of `path` into a byte array.
  `path` may be a `String`, `java.io.File`, or `java.nio.file.Path`."
  ^bytes [path]
  (let [p (if (instance? Path path)
            path
            (Paths/get path (into-array String [])))]
    (Files/readAllBytes p)))

(defn- base64-encoder
  "Return a `Base64.Encoder` instance."
  []
  (Base64/getEncoder))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn image-to-base64
  "Converts an image file to a Base64 `String`.

  Parameters
  ----------
  path : `String` | `File` | `Path`
    The path to the image file.

  Returns
  -------
  `String` | `nil`
    The Base64 representation of the image, or `nil` if an error occurred.

  Notes
  -----
  * The function reads the whole file into memory.  Use it only for
    reasonably sized images.
  * Errors are logged at the `error` level.  The caller receives `nil`."
  [path]
  (try
    (let [bytes (read-file-bytes path)
          encoded (.encode (base64-encoder) bytes)]
      (String. encoded "UTF-8"))
    (catch Exception e
      (log/error e "Failed to convert image to Base64: %s" path)
      nil)))

(defn image-to-base64!
  "Like `image-to-base64` but throws an `ex-info` on failure."
  [path]
  (let [result (image-to-base64 path)]
    (when (nil? result)
      (throw (ex-info "Failed to convert image to Base64" {:path path})))
    result))

(defn image-from-base64
  "Decode a Base64 string back into raw image bytes.

  Parameters
  ----------
  base64-str : `String`
    Base64 representation of an image.

  Returns
  -------
  `bytes` | `nil`
    The decoded byte array, or `nil` if decoding failed."
  ^bytes [base64-str]
  (try
    (.decode (Base64/getDecoder) base64-str)
    (catch IllegalArgumentException e
      (log/error e "Invalid Base64 input")
      nil)))

(defn base64-to-image!
  "Write a Base64 string to an image file.

  Parameters
  ----------
  base64-str : `String`
    Base64 representation of an image.
  out-path   : `String` | `File` | `Path`
    Destination path for the image file.

  Returns
  -------
  `true` on success, throws an `ex-info` on failure."
  [base64-str out-path]
  (let [bytes (image-from-base64 base64-str)]
    (when (nil? bytes)
      (throw (ex-info "Base64 decoding failed" {:base64 base64-str})))
    (try
      (io/make-parents out-path)
      (io/copy (java.io.ByteArrayInputStream. bytes)
               (io/output-stream out-path))
      true
      (catch Exception e
        (throw (ex-info "Failed to write image file" {:out-path out-path} e))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests (example usage)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  ;; Encode
  (def b64 (image-to-base64 "resources/sample.png"))
  ;; Decode and write back
  (base64-to-image! b64 "tmp/sample-out.png"))