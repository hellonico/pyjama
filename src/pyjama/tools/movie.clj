(ns pyjama.tools.movie
 (:require [clj-http.client :as http]
           [cheshire.core :as json]
           [clojure.string :as str])
 (:import (java.net URLEncoder)))

(defn- enc [s] (URLEncoder/encode (str s) "UTF-8"))

(defn- get-json [url]
 (let [resp (http/get url {:headers          {"User-Agent" "pyjama-agent/0.1 (contact: dev@example.com)"}
                           :throw-exceptions false
                           :socket-timeout   8000
                           :conn-timeout     5000})]
  (when-not (<= 200 (:status resp) 299)
   (throw (ex-info "HTTP error" {:status (:status resp) :url url})))
  (json/parse-string (:body resp) true)))

(defn- normalize-spaces [s]
 (-> s
     (str/replace #"\u202F" " ")                            ; NNBSP
     (str/replace #"\u00A0" " ")))                          ; NBSP

(defn- search-pages [{:keys [q lang limit]}]
 (let [url (format "https://%s.wikipedia.org/w/rest.php/v1/search/page?q=%s&limit=%d"
                   (or lang "en") (enc q) (or limit 5))]
  (-> (get-json url) :pages)))

(defn- fetch-summary [{:keys [lang title]}]
 (let [url (format "https://%s.wikipedia.org/api/rest_v1/page/summary/%s"
                   (or lang "en") (enc title))]
  (get-json url)))

(defn- looks-like-film? [{:keys [title description] :as _page}]
 (or (str/includes? (str title) "(film)")
     (re-find #"(?i)\bfilm\b" (str description))))

(defn- parse-year [{:keys [title description]}]
 (some #(when % (Integer/parseInt %))
       [(second (re-find #"\((\d{4}) film\)" (str title)))
        (second (re-find #"\b(19|20)\d{2}\b" (str description)))]))

(def max-bytes 4096)

(defn wiki-movie
 "Tool: find a movie by description or title using Wikipedia.
  Args:
    :message or :query  – user text
    :lang               – 'en' default
    :limit              – candidates to inspect (default 5)
  Returns OBS:
    {:status :ok|:empty
     :query  <string>
     :count  <int>
     :best   {:title :year :url :plot}
     :results [..]
     :text   \"<Title> (<Year>)\\n<Plot>\"}"
 [{:keys [message query lang limit]}]

 (let [q (-> (or query message "")
             str/trim
             (str/replace #"\s+" " "))
       enc-q (URLEncoder/encode q "UTF-8")
       size (count (.getBytes enc-q "UTF-8"))
       _ (println enc-q)
       ]
  (cond (> size max-bytes)
        {:status   :empty
         :too-long true
         :query    q}
        (str/blank? q)
        {:status   :empty
         :too-long false
         :query    q}
        :else
        ; (throw (ex-info "wiki-movie requires a non-empty query" {})))
        (let [pages (or (seq (search-pages {:q q :lang lang :limit (or limit 5)})) [])
              scored (->> pages
                          (map (fn [p]
                                (let [score (+ (if (looks-like-film? p) 10 0)
                                               (if (re-find #"(?i)\bmovie|film\b" q) 2 0)
                                               (if (re-find #"(?i)\bplot|synopsis\b" q) 1 0))]
                                 [score p])))
                          (sort-by first >))
              top (map second scored)
              cand (first top)]
         (if-not cand
          {:status :empty, :query q, :count 0, :results [], :text "(no results)"}
          (let [sum (fetch-summary {:lang lang :title (or (:key cand) (:title cand))})
                title (or (:title sum) (:title cand))
                plot (normalize-spaces (or (:extract sum) ""))
                url (get-in sum [:content_urls :desktop :page])
                year (or (parse-year {:title title :description (:description cand)})
                         (parse-year {:title (:title cand) :description (:description cand)}))
                result {:title title :year year :url url :plot plot}
                text (normalize-spaces (str title (when year (format " (%d)" year))
                                            (when (not (str/blank? plot)) (str "\n" plot))))]
           {:status  :ok
            :query   q
            :count   (count top)
            :best    result
            :results (mapv (fn [p] (select-keys p [:title :description :key])) (take 5 top))
            :text    (if (str/blank? text) "(no plot found)" text)}))))))
