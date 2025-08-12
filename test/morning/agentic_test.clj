(ns morning.agentic-test
 (:require
  [pyjama.agent.core]
  [pyjama.core]
  [clojure.test :refer :all]))

(System/setProperty "agents.edn" "test-resources/agentic/first.edn")

(deftest debug-tool
 (testing "tooling"
  (let [spec (get @pyjama.core/agents-registry :print-test)] ;; or :print-test etc.
   (pyjama.agent.core/explain-tool spec :notify))))

(deftest run-just-tool
 (testing "hello agents"
  (pyjama.agent.core/call {:id :print-test})))

(deftest run-just-agent-flow
 (testing "hello agents"
  (pyjama.agent.core/call {:id :pp :prompt "what is AI?"})))

(deftest alain-turing
 (System/setProperty "agents.edn" "test-resources/agentic/wiki.edn")
 (testing "alain turing"
  (pyjama.agent.core/call {:id     :wiki-summarizer
                      :prompt "Alan Turing early life"})))

(deftest alain-turing-to-file
 (System/setProperty "agents.edn" "test-resources/agentic/wiki-to-file.edn")
 (testing "alain turing"
  (pyjama.agent.core/call {:id     :wiki-summarizer
                      :prompt "Alan Turing early life"})))

(deftest news-analyser
 (System/setProperty "agents.edn" "test-resources/agentic/news.edn")
 (pyjama.agent.core/call {:id     :news-analyzer
                     :prompt "New advances in solar panel efficiency"}))

(deftest web-search
 (System/setProperty "agents.edn" "test-resources/agentic/web.edn")
 (pyjama.agent.core/call {:id     :search-and-summarize
                     :prompt "Clojure best features"}))

(deftest alien-movie
 (System/setProperty "agents.edn" "test-resources/agentic/movie.edn")
 (is {:status :ok, :notified true}
     (pyjama.agent.core/call
      {:id     :movie-psych
       :prompt "A space horror movie where a crew is hunted by a scary creature on their ship"})))

(deftest clojure
 (System/setProperty "agents.edn" "test-resources/agentic/code.edn")
 (let [prompt "document the write-file function in pyjama.tools.file"]
  (->

   (pyjama.agent.core/call
    {:id          :clj-project
     :project-dir "."
     :prompt      prompt})

   )
  ))

(deftest party
 (System/setProperty "agents.edn" "test-resources/agentic/party.edn")
 (pyjama.agent.core/call {:id :party-pack :prompt "A jazz party"}))

(deftest partypdf
 (System/setProperty "agents.edn" "test-resources/agentic/partypdf.edn")
 (pyjama.agent.core/call {:id :party-pack :prompt "A hip hop party"}))


(deftest clojure-agents
 (System/setProperty "agents.edn" "test-resources/agentic/code.edn")
 (let [prompt "document the agent.clj namespace and related edn based DSL. As conclusion, give more ideas for agent flows using that framework."]
  (->

   (pyjama.agent.core/call
    {:id          :clj-project
     :project-dir "."
     :prompt      prompt}))))