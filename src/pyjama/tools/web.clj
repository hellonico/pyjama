(ns pyjama.tools.web
 (:require                                                  ;[clj-http.client :as http]
  [clj-http.client :as http]
  [secrets.core]
  [clojure.string :as str]
  [clojure.walk :as walk])
 )

(defn api-key[] (secrets.core/get-secret "brave-api-key"))
(def brave-api-url "https://api.search.brave.com/res/v1/web/search")

(defn brave->abstracts
 "Return [{:title :url :snippet} ...] from a Brave Search response map."
 [body]
 (let [m (walk/keywordize-keys body)
       results (vec (or (get-in m [:web :results]) []))
       ;; keep Brave's 'mixed' ordering if available
       ordered (if-let [idxs (seq (for [{:keys [type index]} (get-in m [:mixed :main])
                                        :when (= type "web")]
                                   index))]
                (->> idxs (map #(get results %)) (remove nil?))
                results)]
  (->> ordered
       (keep (fn [r]
              (let [title (:title r)
                    url (:url r)
                    desc (:description r)
                    extra (first (:extra_snippets r))
                    snippet (-> (or desc extra title)
                                (str/replace #"<[^>]+>" "") ; drop simple HTML
                                (str/replace #"\s+" " ")
                                (str/trim))]
               (when (and title url)
                {:title   title
                 :url     url
                 :snippet snippet})))))))

(defn brave-search
 "Search Brave with `query`. Optional params: count, country, search-lang."
 [query & {:keys [count country search-lang]}]
 (let [api-key (api-key)
       params (merge {"q" query}
                     (when count {"count" (str count)})
                     (when country {"country" country})
                     (when search-lang {"search_lang" search-lang}))
       response (http/get brave-api-url
                          {:headers      {"X-Subscription-Token" api-key
                                          "Accept"               "application/json"
                                          "Accept-Encoding"      "gzip"}
                           :query-params params
                           :as           :json})]
  (if (= 200 (:status response))
   (:body response)
   (throw (ex-info "Brave API error" {:status (:status response)
                                      :body   (:body response)})))))

(defn web-search
 "Performs a web search via DuckDuckGo Instant Answer API.
  Args:
    :query   -> search query string
    :topk    -> optional number of results to keep (default 3)
  Returns:
    {:status :ok
     :query <string>
     :results [{:title .. :url .. :snippet ..} ...]}"
 [{:keys [message query topk] :or {topk 3}}]
 (let [
       resp (brave-search (or message query))
       abstracts
       (brave->abstracts resp)
       _ (prn abstracts)
       flat-results (flatten abstracts)]
  {:status  :ok
   :query   query
   :results (take topk flat-results)}))
