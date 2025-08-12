(ns pyjama.llamaparse.core
 (:require [clj-http.client :as client]
           [clojure.java.io :as io]
           [secrets.core]
           [clojure.string :as str])
 (:import (java.io File)))

(def base-url "https://api.cloud.llamaindex.ai/api/parsing")
(def upload-endpoint (str base-url "/upload"))
(def job-endpoint (str base-url "/job"))

(defn api-key[]
 (secrets.core/get-secret :llama-cloud-api-key))

(defn extract-filename [file-path]
 (if (str/starts-with? file-path "http")
  (last (str/split file-path #"/"))
  (.getName (io/file file-path))))


(defn- kebab->snake [k]
 (-> k name (str/replace "-" "_")))

(defn- map->multipart [params]
 (map (fn [[k v]]
       {:name    (kebab->snake k)
        :content (str v)})
      params))

(defn parse-file [file-path params]
 (let [file                                                 ;(if (str/starts-with? file-path "http")
       ;(download-file file-path)
       (io/file file-path)
       multipart-data (concat [{:name "file" :content file :mime-type "application/pdf"}]
                              (map->multipart params))
       response (client/post
                 upload-endpoint
                 {:headers   {"Authorization" (str "Bearer " (api-key))}
                  :multipart multipart-data
                  :as        :json})]
  (:body response)))

; https://docs.cloud.llamaindex.ai/llamaparse/output_modes/
(defn get-parsing-result
 ([job-id] (get-parsing-result job-id :markdown))
 ([job-id formatter]
  (let [url (str job-endpoint "/" job-id "/result/" (name formatter))]
   (let [response (client/get
                   url
                   {:headers {"Authorization" (str "Bearer " api-key)
                              "accept"        "application/json"}
                    :as      :json})]
    (formatter (:body response))))))

(defn get-job-status [job-id]
 (let [url (str job-endpoint "/" job-id)]
  (let [response (client/get
                  url
                  {:headers {"Authorization" (str "Bearer " api-key)
                             "accept"        "application/json"}
                   :as      :json})]
   (:body response))))

(defn wait-and-download [job-id output-file]
 (loop []
  (let [status-response (get-job-status job-id)]
   (if status-response
    (let [status (:status status-response)]
     (println "Current Status [" job-id "]:" status)
     (if (= status "SUCCESS")
      (let [markdown-content (get-parsing-result job-id)]
       (spit output-file markdown-content)
       (println "Parsing complete! Output saved to:" output-file))
      (do
       (Thread/sleep 3000)
       (recur))))))))

(defn llama-parser [file-path params output-folder]
 (let [filename (extract-filename file-path)
       _ (println "Uploading file:" file-path)
       response (parse-file file-path params)
       job-id (:id response)
       output-dir (or output-folder (.getParent (io/file file-path)))
       output-file (str (io/file output-dir (str filename ".md")))]
  (if job-id
   (do
    (println "Job submitted, ID:" job-id)
    (wait-and-download job-id output-file))
   (println "Failed to submit job."))))