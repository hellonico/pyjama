(ns morning.claude-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [pyjama.core]
            [pyjama.claude.core]))

(def api-key (System/getenv "ANTHROPIC_API_KEY"))

;; =============================================================================
;; Basic API Tests
;; =============================================================================

(deftest test-claude-basic-api-call
  (testing "Should make basic Claude API call"
    (when api-key
      (let [result (pyjama.claude.core/claude
                     {:prompt "What is 2+2? Please give a brief answer."
                      :model "claude-3-5-sonnet-20241022"})]
        (is (string? result))
        (is (not (str/blank? result)))))))

(deftest test-claude-streaming-response
  (testing "Should handle streaming Claude response"
    (when api-key
      (let [result (pyjama.claude.core/claude
                     {:prompt "Explain quantum computing in one sentence."
                      :stream true})]
        (is (some? result))))))

(deftest test-claude-main-interface
  (testing "Should work through main pyjama-call interface"
    (when api-key
      (let [result (pyjama.core/call
                     {:impl :claude
                      :prompt "Hello, how are you?"})]
        (is (string? result))
        (is (not (str/blank? result)))))))

;; =============================================================================
;; Structured Output Tests
;; =============================================================================

(deftest test-claude-structured-output
  (testing "Should get structured response from Claude"
    (when api-key
      (let [schema {:type "object"
                    :properties {:answer {:type "string"}
                                :confidence {:type "number"}}
                    :required ["answer" "confidence"]}
            result (pyjama.claude.core/get-structured-response
                    "What is the capital of France? Answer with high confidence."
                    schema)]
        (is (map? result))
        (is (contains? result :answer))
        (is (contains? result :confidence))))))

;; =============================================================================
;; Model Configuration Tests
;; =============================================================================

(deftest test-claude-different-models
  (testing "Should work with different Claude models"
    (when api-key
      (doseq [model ["claude-3-5-sonnet-20241022" 
                     "claude-3-opus-20240229"
                     "claude-3-sonnet-20240229"]]
        (let [result (pyjama.claude.core/claude
                       {:prompt "Say hello"
                        :model model})]
          (is (string? result))
          (is (not (str/blank? result))))))))

;; =============================================================================
;; Parameter Tests
;; =============================================================================

(deftest test-claude-temperature-parameter
  (testing "Should respect temperature parameter"
    (when api-key
      (let [result (pyjama.claude.core/claude
                     {:prompt "Write a creative story"
                      :temperature 0.9})]
        (is (string? result))
        (is (not (str/blank? result)))))))

(deftest test-claude-max-tokens-parameter
  (testing "Should respect max_tokens parameter"
    (when api-key
      (let [result (pyjama.claude.core/claude
                     {:prompt "Write a very long story"
                      :max_tokens 100})]
        (is (string? result))
        (is (<= (count result) 150))))))

;; =============================================================================
;; Error Handling Tests
;; =============================================================================

(deftest test-claude-missing-api-key
  (testing "Should handle missing API key gracefully"
    (let [original-key (System/getenv "ANTHROPIC_API_KEY")]
      (try
        (System/setProperty "ANTHROPIC_API_KEY" "")
        (let [result (pyjama.claude.core/claude
                       {:prompt "Test"})]
          (is (or (nil? result) (string? result))))
        (finally
          (when original-key
            (System/setProperty "ANTHROPIC_API_KEY" original-key)))))))

;; =============================================================================
;; Integration Tests
;; =============================================================================

(deftest test-claude-with-agents
  (testing "Should work with agent configuration"
    (when api-key
      (let [result (pyjama.core/call
                     {:id :claude-test-agent
                      :impl :claude
                      :prompt "What is the meaning of life?"})]
        (is (string? result))
        (is (not (str/blank? result)))))))

(deftest test-claude-streaming-integration
  (testing "Should handle streaming through main interface"
    (when api-key
      (let [result (pyjama.core/call
                     {:impl :claude
                      :prompt "Write a short poem"
                      :stream true})]
        (is (some? result)))))) 