(ns pyjama.agent.loop-test
  (:require [clojure.test :refer :all]
            [pyjama.agent.core :as agent]
            [pyjama.core :as pj]))

(defn create-test-items [_]
  {:items [{:id 1 :name "Item 1" :value 10}
           {:id 2 :name "Item 2" :value 20}
           {:id 3 :name "Item 3" :value 30}]})

(defn process-test-item [{:keys [item]}]
  {:processed true
   :item-id (:id item)
   :item-name (:name item)
   :doubled-value (* 2 (:value item))})

(def test-loop-agent
  {:description "Test agent for loop functionality"
   :start :create-items
   :max-steps 20

   :tools
   {:create-items {:fn 'pyjama.agent.loop-test/create-test-items}
    :process-item {:fn 'pyjama.agent.loop-test/process-test-item}}

   :steps
   {:create-items
    {:tool :create-items
     :next :loop-all}

    :loop-all
    {:loop-over [:obs :items]
     :loop-body :process-one
     :next :done}

    :process-one
    {:tool :process-item
     :args {:item "{{loop-item}}"}
     :next :done}}})

(deftest test-basic-loop
  (testing "Basic loop iteration"
    (let [result (agent/call {:id :test-loop-agent
                              :agent test-loop-agent})]
      (is (= :ok (:status result)))
      (is (= 3 (:loop-count result)))
      (is (= 3 (count (:loop-results result))))

      ;; Check first result
      (let [first-result (first (:loop-results result))]
        (is (:processed first-result))
        (is (= 1 (:item-id first-result)))
        (is (= "Item 1" (:item-name first-result)))
        (is (= 20 (:doubled-value first-result))))

      ;; Check last result
      (let [last-result (last (:loop-results result))]
        (is (= 3 (:item-id last-result)))
        (is (= 60 (:doubled-value last-result)))))))

(def empty-loop-agent
  {:description "Test agent for empty collection"
   :start :create-empty
   :max-steps 10

   :tools
   {:create-empty {:fn (fn [_] {:items []})}}

   :steps
   {:create-empty
    {:tool :create-empty
     :next :loop-all}

    :loop-all
    {:loop-over [:obs :items]
     :loop-body :process-one
     :next :done}

    :process-one
    {:tool :process-item
     :args {:item "{{loop-item}}"}
     :next :done}}})

(deftest test-empty-loop
  (testing "Loop with empty collection"
    (let [result (agent/call {:id :empty-loop-agent
                              :agent empty-loop-agent})]
      (is (= :ok (:status result)))
      (is (= 0 (:loop-count result)))
      (is (= "No items to process" (:message result))))))

(defn create-nested-data [_]
  {:projects [{:id 1 :name "Project A" :tasks [{:id 101 :title "Task 1"}
                                               {:id 102 :title "Task 2"}]}
              {:id 2 :name "Project B" :tasks [{:id 201 :title "Task 3"}]}]})

(defn process-task [{:keys [task project-name]}]
  {:result (str "Processed " (:title task) " from " project-name)})

(def nested-loop-agent
  {:description "Test nested loops"
   :start :create-data
   :max-steps 50

   :tools
   {:create-data {:fn 'pyjama.agent.loop-test/create-nested-data}
    :get-tasks {:fn (fn [{:keys [project]}] {:tasks (:tasks project)})}
    :process-task {:fn 'pyjama.agent.loop-test/process-task}}

   :steps
   {:create-data
    {:tool :create-data
     :next :loop-projects}

    :loop-projects
    {:loop-over [:obs :projects]
     :loop-body :get-tasks
     :next :done}

    :get-tasks
    {:tool :get-tasks
     :args {:project "{{loop-item}}"}
     :next :loop-tasks}

    :loop-tasks
    {:loop-over [:obs :tasks]
     :loop-body :process-task-step
     :next :done}

    :process-task-step
    {:tool :process-task
     :args {:task "{{loop-item}}"
            :project-name "{{ctx.loop-item.name}}"}
     :next :done}}})

(deftest test-nested-loops
  (testing "Nested loop iteration"
    (let [result (agent/call {:id :nested-loop-agent
                              :agent nested-loop-agent})]
      (is (= :ok (:status result)))
      (is (= 2 (:loop-count result)))

      ;; First project should have 2 tasks processed
      (let [first-project-result (first (:loop-results result))]
        (is (= 2 (:loop-count first-project-result))))

      ;; Second project should have 1 task processed
      (let [second-project-result (second (:loop-results result))]
        (is (= 1 (:loop-count second-project-result)))))))

(comment
  ;; Run tests manually
  (run-tests 'pyjama.agent.loop-test)

  ;; Test individual cases
  (test-basic-loop)
  (test-empty-loop)
  (test-nested-loops))
