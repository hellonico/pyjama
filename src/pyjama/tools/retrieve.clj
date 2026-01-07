(ns pyjama.tools.retrieve
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.io File)))

;
; scoring
;

(defn- safe-str [x]
  (cond
    (nil? x) ""
    (string? x) x
    :else (pr-str x)))

(defn- score [q s]
  (let [q* (-> q safe-str str/lower-case)
        s* (-> s safe-str str/lower-case)]
    (+ (if (str/includes? s* q*) 5 0)
       (reduce + (map #(if (str/includes? s* %) 1 0)
                      (remove str/blank? (str/split q* #"\W+")))))))


(defn pick-snippets
  "Args: {:message <str> :files [{:file :content} ...] :max-files :max-chars :ctx}
  Returns {:status :ok :context [{:file :chunk}] :text <joined>}"
  [{:keys [message files max-files max-chars ctx]
    :or   {max-files 6 max-chars 8000}}]
  (let [question (safe-str message)
        ;; allow fallback from ctx if :files arg not provided
        files    (or files (:project-files ctx) [])

        ;; guard: filter out any nil/empty content entries
        files    (->> files
                      (filter some?)
                      (filter #(string? (:file %)))
                      (map #(update % :content safe-str)))]
    (if (empty? files)
      {:status :empty
       :context []
       :text "(no project files available)"}
      (let [scored  (->> files
                         (map (fn [{:keys [file content]}]
                                {:file file
                                 :score (score question content)
                                 :content content}))
                         (sort-by :score >)
                         (take max-files))
            chunks  (for [{:keys [file content]} scored]
                      {:file file
                       :chunk (subs content 0 (min (count content) 1200))})
            joined  (->> chunks
                         (map (fn [{:keys [file chunk]}]
                                (str "### " file "\n\n```clojure\n" chunk "\n```\n")))
                         (apply str))
            text    (if (> (count joined) max-chars)
                      (subs joined 0 max-chars)
                      joined)]
        {:status  :ok
         :context (vec chunks)
         :text    text}))))

;
; codebase
;

(defn read-code-base [{:keys [dir] :as ctx}]
  (let [root (str/trim (or dir "."))
        f    (io/file root)]
    (prn ctx)
    (when-not (.exists f)      (throw (ex-info "Project dir not found" {:dir root})))
    (when-not (.isDirectory f) (throw (ex-info "Not a directory" {:dir root})))
    (let [files (->> (file-seq f)
                     (filter #(and (.isFile ^File %)
                                   (re-find #"\.(clj|cljc|cljs|edn|md)$" (.getName ^File %))))
                     (map (fn [f] {:file (.getAbsolutePath ^File f)
                                   :content (slurp f)}))
                     vec)]
      {:status :ok :files files})))

;
; classify
;

(defn classify
  "Returns {:status :document|:test|:reorg|:feature :topic <str>}"
  [{:keys [message]}]
  (let [q (str/lower-case (or message ""))]
    (cond
      (re-find #"\b(doc|document|guide|how[- ]to|readme)\b" q) {:status :document :topic message}
      (re-find #"\b(test|spec|unit|integration)\b" q) {:status :test :topic message}
      (re-find #"\b(reorg|refactor|structure|module)\b" q) {:status :reorg :topic message}
      (re-find #"\b(feature|implement|add|support)\b" q) {:status :feature :topic message}
      :else {:status :document :topic message})))
