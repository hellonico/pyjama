(ns pyjama.tools.shell-test
  (:require [clojure.test :refer [deftest is testing]]
            [pyjama.tools.shell :as shell]
            [clojure.java.io :as io]))

(deftest test-execute-command
  (testing "Simple echo command"
    (let [result (shell/execute-command {:command "echo 'Hello World'"})]
      (is (= :ok (:status result)))
      (is (= 0 (:exit result)))
      (is (re-find #"Hello World" (:out result)))))

  (testing "Command as vector"
    (let [result (shell/execute-command {:command ["echo" "test"]})]
      (is (= :ok (:status result)))
      (is (= 0 (:exit result)))))

  (testing "Working directory"
    (let [result (shell/execute-command {:command "pwd" :dir "/tmp"})]
      (is (= :ok (:status result)))
      (is (re-find #"/tmp" (:out result)))))

  (testing "Failed command"
    (let [result (shell/execute-command {:command "ls /nonexistent-directory-xyz"})]
      (is (= :error (:status result)))
      (is (not= 0 (:exit result))))))

(deftest test-env-var-expansion
  (testing "Expand $HOME in command string"
    (let [result (shell/execute-command {:command "echo $HOME"})]
      (is (= :ok (:status result)))
      (is (seq (:out result)))
      (is (not (re-find #"\$HOME" (:out result))))))

  (testing "Expand ${USER} in command string"
    (let [result (shell/execute-command {:command "echo ${USER}"})]
      (is (= :ok (:status result)))
      (is (seq (:out result)))
      (is (not (re-find #"\$\{USER\}" (:out result))))))

  (testing "Disable env var expansion"
    (let [result (shell/execute-command {:command "echo '$HOME'"
                                         :expand-env? false})]
      (is (= :ok (:status result)))
      (is (re-find #"\$HOME" (:out result)))))

  (testing "Mixed env vars in vector command"
    (let [result (shell/execute-command {:command ["echo" "$HOME"]})]
      (is (= :ok (:status result)))
      (is (seq (:out result))))))

(deftest test-glob-expansion
  (testing "Glob expansion with *.clj pattern"
    ;; Create test files
    (let [test-dir (io/file "test-glob-temp")]
      (.mkdirs test-dir)
      (spit (io/file test-dir "file1.clj") "test")
      (spit (io/file test-dir "file2.clj") "test")
      (spit (io/file test-dir "file3.txt") "test")

      (try
        (let [result (shell/execute-command
                      {:command ["ls" "test-glob-temp/*.clj"]
                       :expand-glob? true})]
          (is (= :ok (:status result)))
          (is (re-find #"file1.clj" (:out result)))
          (is (re-find #"file2.clj" (:out result)))
          (is (not (re-find #"file3.txt" (:out result)))))
        (finally
          ;; Cleanup
          (doseq [f (.listFiles test-dir)]
            (.delete f))
          (.delete test-dir)))))

  (testing "Disable glob expansion"
    (let [result (shell/execute-command
                  {:command ["echo" "*.clj"]
                   :expand-glob? false})]
      (is (= :ok (:status result)))
      (is (re-find #"\*\.clj" (:out result))))))

(deftest test-run-script
  (testing "Simple shell script"
    (let [result (shell/run-script
                  {:script "echo 'Line 1'\necho 'Line 2'"})]
      (is (= :ok (:status result)))
      (is (re-find #"Line 1" (:out result)))
      (is (re-find #"Line 2" (:out result)))))

  (testing "Script with env vars"
    (let [result (shell/run-script
                  {:script "echo $HOME"})]
      (is (= :ok (:status result)))
      (is (seq (:out result)))
      (is (not (re-find #"\$HOME" (:out result)))))))
