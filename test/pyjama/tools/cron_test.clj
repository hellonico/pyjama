(ns pyjama.tools.cron-test
  (:require [clojure.test :refer [deftest is testing]]
            [pyjama.tools.cron :as cron]))

(deftest test-schedule-parsing
  (testing "Parse @hourly"
    (let [result (cron/schedule-task
                  {:id "test-hourly"
                   :schedule "@hourly"
                   :task #(println "Test")})]
      (is (= :ok (:status result)))
      (is (= "test-hourly" (:id result)))
      (cron/cancel-task {:id "test-hourly"})))

  (testing "Parse @daily"
    (let [result (cron/schedule-task
                  {:id "test-daily"
                   :schedule "@daily"
                   :task #(println "Test")})]
      (is (= :ok (:status result)))
      (cron/cancel-task {:id "test-daily"})))

  (testing "Parse interval */5 * * * *"
    (let [result (cron/schedule-task
                  {:id "test-interval"
                   :schedule "*/5 * * * *"
                   :task #(println "Test")})]
      (is (= :ok (:status result)))
      (is (= 300 (:period-seconds result))) ; 5 minutes = 300 seconds
      (cron/cancel-task {:id "test-interval"}))))

(deftest test-task-management
  (testing "List tasks"
    (let [_ (cron/schedule-task
             {:id "test-list"
              :schedule "@hourly"
              :task #(println "Test")
              :description "Test task"})
          result (cron/list-tasks)]
      (is (= :ok (:status result)))
      (is (>= (:count result) 1))
      (cron/cancel-task {:id "test-list"})))

  (testing "Cancel task"
    (cron/schedule-task
     {:id "test-cancel"
      :schedule "@hourly"
      :task #(println "Test")})
    (let [result (cron/cancel-task {:id "test-cancel"})]
      (is (= :ok (:status result)))
      (is (= "test-cancel" (:id result))))))

(deftest test-run-once-after
  (testing "Schedule one-time task"
    (let [executed (atom false)
          result (cron/run-once-after
                  {:id "test-once"
                   :delay 1
                   :task #(reset! executed true)
                   :description "One-time test"})]
      (is (= :ok (:status result)))
      (is (= 1 (:delay-seconds result)))
      (Thread/sleep 1500) ; Wait for task to execute
      (is @executed "Task should have executed")
      ; Task should auto-remove itself
      (let [tasks (cron/list-tasks)]
        (is (not (some #(= "test-once" (:id %)) (:tasks tasks))))))))
