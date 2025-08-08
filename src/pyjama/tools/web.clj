(ns pyjama.tools.web
 (:require [clj-http.client :as http]
           [cheshire.core :as json]))

(defn web-search
 "Performs a web search via DuckDuckGo Instant Answer API.
  Args:
    :query   -> search query string
    :topk    -> optional number of results to keep (default 3)
  Returns:
    {:status :ok
     :query <string>
     :results [{:title .. :url .. :snippet ..} ...]}"
 [{:keys [query topk] :or {topk 3}}]
 (let [url "https://api.duckduckgo.com/"
       resp (http/get url {:query-params {"q" query
                                          "format" "json"
                                          "no_redirect" 1
                                          "no_html" 1}
                           :as :text})
       body (json/parse-string (:body resp) true)
       abstracts (->> (:RelatedTopics body)
                      (keep (fn [t]
                             (cond
                              (:Text t)
                              {:title (:Text t)
                               :url   (:FirstURL t)
                               :snippet (:Text t)}

                              (seq (:Topics t))
                              (map (fn [st]
                                    {:title (:Text st)
                                     :url   (:FirstURL st)
                                     :snippet (:Text st)})
                                   (:Topics t))))))
       flat-results (flatten abstracts)]
  {:status  :ok
   :query   query
   :results (take topk flat-results)}))
