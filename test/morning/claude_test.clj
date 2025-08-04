(ns morning.claude-test
  (:require [clojure.test :refer :all]
            [pyjama.core]
            [pyjama.claude.core]))

(deftest test-claude-basic
  (testing "Should make basic Claude API call"
    (let [api-key (System/getenv "ANTHROPIC_API_KEY")]
      (when api-key
        (let [result (pyjama.claude.core/claude
                       {:prompt "What is 2+2?"
                        :model "claude-3-5-sonnet-20241022"})]
          (is (string? result)))))))

(deftest test-claude-integration
  (testing "Should work through main pyjama-call interface"
    (let [api-key (System/getenv "ANTHROPIC_API_KEY")]
      (when api-key
        (let [result (pyjama.core/call
                       {:impl :claude
                        :prompt "Hello, how are you?"})]
          (is (string? result))))))) 