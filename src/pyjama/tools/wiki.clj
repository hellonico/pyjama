(ns pyjama.tools.wiki
 (:import (java.net URLEncoder))
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
  (if (<= 200 (:status resp) 299)
   (json/parse-string (:body resp) true)
   (throw (ex-info "HTTP error" {:status (:status resp) :url url})))))

(defn- search-titles [{:keys [q lang limit]}]
 ;; Wikipedia REST search (title)
 (let [url (format "https://%s.wikipedia.org/w/rest.php/v1/search/title?q=%s&limit=%d"
                   (or lang "en") (enc q) (or limit 3))]
  (-> (get-json url) :pages)))

(defn- fetch-summary [{:keys [lang title]}]
 (let [url (format "https://%s.wikipedia.org/api/rest_v1/page/summary/%s"
                   (or lang "en") (enc title))]
  (select-keys (get-json url) [:title :extract :thumbnail :content_urls :pageid])))

(defn wiki-search
 "Tool: Search Wikipedia and return text for the LLM to summarize.
  Args:
    :message OR :query  → search query (string)
    :lang               → 'en' (default)
    :topk               → number of results (default 3)
  Returns observation with:
    :status :ok, :results [{:title :extract :url ...}], and :text (joined extracts)."
 [{:keys [message query lang topk] :as _args}]
 (let [q (or query message)
       k (or topk 3)]
  (when (str/blank? q)
   (throw (ex-info "wiki-search requires a :query or :message" {})))
  (let [pages (search-titles {:q q :lang (or lang "en") :limit k})
        titles (map :title pages)
        results (->> titles
                     (map #(fetch-summary {:lang lang :title %}))
                     (map (fn [{:keys [title extract content_urls] :as m}]
                           (merge
                            {:title   title
                             :url     (get-in content_urls [:desktop :page])
                             :extract (or extract "")}
                            (dissoc m :content_urls))))
                     (remove (comp str/blank? :extract))
                     (take k)
                     vec)
        text (->> results (map (fn [{:keys [title extract]}]
                                (str "## " title "\n" extract)))
                  (str/join "\n\n"))]
   ;; Returning :text ensures your agent’s summarizer step gets clean context
   {:status  :ok
    :query   q
    :count   (count results)
    :results results
    :text    (if (str/blank? text) "(no extracts found)" text)})))

