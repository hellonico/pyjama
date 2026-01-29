(ns pyjama.trace-context-test
  "Tests for trace context variable interpolation"
  (:require [clojure.test :refer [deftest is testing]]
            [pyjama.agent.core :as agent]
            [pyjama.core]
            [pyjama.io.template]))

(deftest simple-trace-access-test
  (testing "Trace values accessible via template interpolation"
    (let [captured-value (atom nil)]
      (with-redefs [;; Stub template rendering tointerpolate {{trace[-1].obs.value}}
                    pyjama.io.template/render-args-deep
                    (fn [args ctx _params]
                      (into {} (for [[k v] args]
                                 [k (if (and (string? v) (re-find #"\{\{trace\[-1\].obs.value\}\}" v))
                                      (get-in ctx [:trace (dec (count (:trace ctx))) :obs :value])
                                      v)])))

                    pyjama.core/agents-registry
                    (atom {:simple-trace
                           {:description "Simple trace test"
                            :start :produce
                            :max-steps 5
                            :tools
                            {:produce {:fn (fn [_] {:status :ok :value "DATA" :text "produced"})}
                             :consume {:fn (fn [args]
                                             (reset! captured-value (:val args))
                                             {:status :ok :text "consumed"})}}
                            :steps
                            {:produce
                             {:tool :produce
                              :next :consume}

                             :consume
                             {:tool :consume
                              :args {:val "{{trace[-1].obs.value}}"}
                              :terminal? true}}}})]

        (agent/call {:id :simple-trace})

        (testing "Trace value was correctly interpolated"
          (is (= "DATA" @captured-value)))))))

(comment
  ;; Run tests
  (clojure.test/run-tests 'pyjama.trace-context-test))