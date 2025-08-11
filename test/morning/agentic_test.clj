(ns morning.agentic-test
 (:require
  [pyjama.agent]
  [pyjama.core]
  [clojure.test :refer :all]))

(System/setProperty "agents.edn" "test-resources/agentic/first.edn")

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

(deftest alain-turing
 (System/setProperty "agents.edn" "test-resources/agentic/wiki.edn")
 (testing "alain turing"
  (pyjama.agent/call {:id     :wiki-summarizer
                      :prompt "Alan Turing early life"})))

(deftest alain-turing-to-file
 (System/setProperty "agents.edn" "test-resources/agentic/wiki-to-file.edn")
 (testing "alain turing"
  (pyjama.agent/call {:id     :wiki-summarizer
                      :prompt "Alan Turing early life"})))

(deftest news-analyser
 (System/setProperty "agents.edn" "test-resources/agentic/news.edn")
 (pyjama.agent/call {:id     :news-analyzer
                     :prompt "New advances in solar panel efficiency"}))

(deftest web-search
 (System/setProperty "agents.edn" "test-resources/agentic/web.edn")
 (pyjama.agent/call {:id     :search-and-summarize
                     :prompt "Clojure best features"}))

(deftest alien-movie
 (System/setProperty "agents.edn" "test-resources/agentic/movie.edn")
 (is {:status :ok, :notified true}
     (pyjama.agent/call
      {:id     :movie-psych
       :prompt "A space horror movie where a crew is hunted by a scary creature on their ship"})))

(deftest clojure
 (System/setProperty "agents.edn" "test-resources/agentic/code.edn")
 (let [prompt "document the write-file function in pyjama.tools.file"]
  (->

   (pyjama.agent/call
    {:id          :clj-project
     :project-dir "."
     :prompt      prompt})

   )
  ))

(deftest clojure-agents
 (System/setProperty "agents.edn" "test-resources/agentic/code.edn")
 (let [prompt "document the agent.clj namespace and related DSL."]
  (->

   (pyjama.agent/call
    {:id          :clj-project
     :project-dir "."
     :prompt      prompt})

   )
  ))

(deftest party
 (System/setProperty "agents.edn" "test-resources/agentic/party.edn")
 (pyjama.agent/call {:id :party-pack :prompt "A jazz party"}))