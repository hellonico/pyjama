(ns morning.agentic
 (:require
  [pyjama.agent]
  [pyjama.core]
  [clojure.test :refer :all]))

(System/setProperty "agents.edn" "test-resources/agentic.edn")

(deftest debug-tool
 (testing "tooling"
  (let [spec (get @pyjama.core/agents-registry :print-test)] ;; or :print-test etc.
   (pyjama.agent/explain-tool spec :notify))))

(deftest run-just-tool
 (testing "hello agents"
  (pyjama.agent/call {:id :print-test})))

(deftest run-just-agent-flow
 (testing "hello agents"
  (pyjama.agent/call {:id :pp :prompt "what is AI?"})))