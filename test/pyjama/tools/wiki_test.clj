(ns pyjama.tools.wiki-test
  (:require
    [clojure.test :refer :all]
    [clj-http.client :as http]
    [cheshire.core :as json]
    [pyjama.tools.wiki :as wiki]))

(defn- ok [m]
  {:status 200
   :body   (json/generate-string m)})

(deftest wiki-search-happy-path
  (let [calls (atom [])]
    (with-redefs [http/get
                  (fn [url _opts]
                    (swap! calls conj url)
                    (cond
                      ;; search endpoint
                      (re-find #"/w/rest\.php/v1/search/title" url)
                      (ok {:pages [{:title "Cat"} {:title "Dog"} {:title "Empty"}]})

                      ;; summaries
                      (re-find #"/api/rest_v1/page/summary/Cat" url)
                      (ok {:title "Cat"
                           :extract "Cats are small, carnivorous mammals."
                           :content_urls {:desktop {:page "https://en.wikipedia.org/wiki/Cat"}}
                           :pageid 1})

                      (re-find #"/api/rest_v1/page/summary/Dog" url)
                      (ok {:title "Dog"
                           :extract "Dogs are domesticated descendants of wolves."
                           :content_urls {:desktop {:page "https://en.wikipedia.org/wiki/Dog"}}
                           :pageid 2})

                      ;; this one has a blank extract → should be filtered out
                      (re-find #"/api/rest_v1/page/summary/Empty" url)
                      (ok {:title "Empty"
                           :extract ""
                           :content_urls {:desktop {:page "https://example/empty"}}
                           :pageid 3})

                      :else
                      {:status 404 :body ""}))]

      (let [obs (wiki/wiki-search {:message "cats and dogs" :lang "en" :topk 3})]
        (testing "returns shape"
          (is (= :ok (:status obs)))
          (is (= "cats and dogs" (:query obs)))
          (is (= 2 (:count obs)))                         ;; one blank extract filtered out
          (is (= 2 (count (:results obs)))))

        (testing "joined text contains headings + extracts"
          (let [t (:text obs)]
            (is (re-find #"## Cat" t))
            (is (re-find #"Cats are small" t))
            (is (re-find #"## Dog" t))
            (is (re-find #"Dogs are domesticated" t))
            ;; ensure the 'Empty' one didn't get in
            (is (not (re-find #"## Empty" t)))))

        (testing "urls are carried into :results"
          (is (= "https://en.wikipedia.org/wiki/Cat"
                 (get-in obs [:results 0 :url])))
          (is (= "https://en.wikipedia.org/wiki/Dog"
                 (get-in obs [:results 1 :url]))))

        (testing "made calls to both search and summaries"
          (is (some #(re-find #"/w/rest\.php/v1/search/title" %) @calls))
          (is (some #(re-find #"/api/rest_v1/page/summary/Cat" %) @calls))
          (is (some #(re-find #"/api/rest_v1/page/summary/Dog" %) @calls)))))))

(deftest wiki-search-prefers-query-over-message
  (with-redefs [http/get
                (fn [url _]
                  (cond
                    (re-find #"/w/rest\.php/v1/search/title\?q=hedgehog" url)
                    (ok {:pages [{:title "Hedgehog"}]})

                    (re-find #"/api/rest_v1/page/summary/Hedgehog" url)
                    (ok {:title "Hedgehog"
                         :extract "Hedgehogs are spiny mammals."
                         :content_urls {:desktop {:page "https://en.wikipedia.org/wiki/Hedgehog"}}
                         :pageid 42})

                    :else {:status 404 :body ""}))]

    (let [obs (wiki/wiki-search {:message "ignored" :query "hedgehog" :lang "en" :topk 1})]
      (is (= :ok (:status obs)))
      (is (= "hedgehog" (:query obs)))
      (is (re-find #"## Hedgehog" (:text obs))))))

(deftest wiki-search-blank-query-throws
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"requires a :query or :message"
                        (wiki/wiki-search {:message ""})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"requires a :query or :message"
                        (wiki/wiki-search {:query nil}))))

;; Mark as :integration so you can include/exclude it via test selectors
;;   clojure -X:test :include '[:integration]'
;;   clojure -X:test :exclude '[:integration]'
(deftest ^:integration wiki-search-live-hits-wikipedia
  ;; This will perform real HTTP calls via clj-http (no stubbing).
  (let [q   "Alan Turing"
        obs (wiki/wiki-search {:message q :lang "en" :topk 2})]

    (testing "basic shape"
      (is (= :ok (:status obs)))
      (is (string? (:query obs)))
      (is (map? obs)))

    (testing "results present"
      (is (>= (:count obs) 1) "Expect at least one non-empty extract")
      (is (vector? (:results obs)))
      (is (every? map? (:results obs))))

    (testing "joined text exists"
      (is (string? (:text obs)))
      (is (not (clojure.string/blank? (:text obs))))
      ;; very loose content check: should mention Turing somewhere in extracts
      (is (re-find #"(?i)turing" (:text obs))
          "Expected joined extracts to mention 'Turing'"))

    (testing "result entries carry minimal fields"
      (let [r0 (first (:results obs))]
        (is (contains? r0 :title))
        (is (contains? r0 :extract))
        ;; url can occasionally be absent if the API changes, so don’t assert hard
        (is (or (nil? (:url r0))
                (string? (:url r0))))))))
