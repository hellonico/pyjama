(ns pyjama.tools.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [pyjama.tools.registry :as registry]))

;; Mock tool namespace for testing
(defn mock-tool-1 [obs] {:result "tool-1" :input obs})
(defn mock-tool-2 [obs] {:result "tool-2" :input obs})

(defn register-tools!
  "Mock register-tools! function for testing"
  []
  {:tool-1 {:fn mock-tool-1 :description "First mock tool"}
   :tool-2 {:fn mock-tool-2 :description "Second mock tool"}})

(deftest test-expand-wildcard-tools
  (testing "Simple wildcard expansion"
    (let [result (registry/expand-wildcard-tools
                  {:* 'pyjama.tools.registry-test})]
      (is (contains? result :tool-1))
      (is (contains? result :tool-2))
      (is (= mock-tool-1 (get-in result [:tool-1 :fn])))
      (is (= "First mock tool" (get-in result [:tool-1 :description])))))

  (testing "Prefixed wildcard expansion"
    (let [result (registry/expand-wildcard-tools
                  {:mock/* 'pyjama.tools.registry-test})]
      (is (contains? result :mock/tool-1))
      (is (contains? result :mock/tool-2))
      (is (= mock-tool-1 (get-in result [:mock/tool-1 :fn])))))

  (testing "Mixed wildcards and explicit tools"
    (let [custom-fn (fn [x] x)
          result (registry/expand-wildcard-tools
                  {:* 'pyjama.tools.registry-test
                   :custom-tool {:fn custom-fn :description "Custom"}})]
      (is (contains? result :tool-1))
      (is (contains? result :tool-2))
      (is (contains? result :custom-tool))
      (is (= custom-fn (get-in result [:custom-tool :fn])))))

  (testing "Explicit tools override wildcards"
    (let [override-fn (fn [x] {:overridden true})
          result (registry/expand-wildcard-tools
                  {:* 'pyjama.tools.registry-test
                   :tool-1 {:fn override-fn :description "Overridden"}})]
      (is (= override-fn (get-in result [:tool-1 :fn])))
      (is (= "Overridden" (get-in result [:tool-1 :description]))))))

(deftest test-register-namespace
  (testing "Manual namespace registration"
    (let [tools (registry/register-namespace! 'pyjama.tools.registry-test)]
      (is (= 2 (count tools)))
      (is (contains? tools :tool-1))
      (is (contains? tools :tool-2))))

  (testing "Get registered namespace tools"
    (let [tools (registry/get-namespace-tools 'pyjama.tools.registry-test)]
      (is (some? tools))
      (is (contains? tools :tool-1)))))

(deftest test-all-tools
  (testing "Merge all registered tools"
    (registry/register-namespace! 'pyjama.tools.registry-test)
    (let [all (registry/all-tools)]
      (is (contains? all :tool-1))
      (is (contains? all :tool-2)))))

(deftest test-normalize-tool-def
  (testing "Normalize full tool definition (no change)"
    (let [tool-def {:fn 'my-ns/my-fn :description "My tool"}
          result (registry/normalize-tool-def tool-def)]
      (is (= tool-def result))))

  (testing "Normalize symbol reference"
    (let [result (registry/normalize-tool-def 'plane-client.pyjama.tools/create-issue)]
      (is (= 'plane-client.pyjama.tools/create-issue (:fn result)))
      (is (string? (:description result)))))

  (testing "Normalize var reference"
    (let [result (registry/normalize-tool-def #'mock-tool-1)]
      (is (= #'mock-tool-1 (:fn result)))
      (is (string? (:description result)))))

  (testing "Normalize anonymous function"
    (let [anon-fn (fn [obs] {:result obs})
          result (registry/normalize-tool-def anon-fn)]
      (is (= anon-fn (:fn result)))
      (is (= "Tool: anonymous-fn" (:description result))))))

(deftest test-normalize-tools-map
  (testing "Normalize mixed tools map"
    (let [tools-map {:tool-1 'my-ns/fn-1
                     :tool-2 {:fn 'my-ns/fn-2 :description "Custom"}
                     :tool-3 #'mock-tool-1}
          result (registry/normalize-tools-map tools-map)]
      (is (= 'my-ns/fn-1 (get-in result [:tool-1 :fn])))
      (is (string? (get-in result [:tool-1 :description])))
      (is (= 'my-ns/fn-2 (get-in result [:tool-2 :fn])))
      (is (= "Custom" (get-in result [:tool-2 :description])))
      (is (= #'mock-tool-1 (get-in result [:tool-3 :fn]))))))

(deftest test-direct-function-with-wildcards
  (testing "Mix direct function references with wildcards"
    (let [custom-fn (fn [obs] {:custom true})
          result (registry/expand-wildcard-tools
                  {:* 'pyjama.tools.registry-test
                   :custom-tool custom-fn
                   :another-tool 'my-ns/another-fn})]
      (is (contains? result :tool-1))
      (is (contains? result :tool-2))
      (is (contains? result :custom-tool))
      (is (contains? result :another-tool))
      (is (= custom-fn (get-in result [:custom-tool :fn])))
      (is (= 'my-ns/another-fn (get-in result [:another-tool :fn]))))))
