(ns pyjama.validate
 (:require [clojure.string :as str]))

(defn- check-validator
 "Given an output string and one validator entry [k v], return
  [true msg] if it passes, or [false msg] if it fails."
 [[k v] output]
 (case k
  :min-length
  (let [pass? (>= (count output) v)]
   [pass? (str "expected length ≥ " v ", got " (count output))])

  :max-length
  (let [pass? (<= (count output) v)]
   [pass? (str "expected length ≤ " v ", got " (count output))])

  :contains
  (let [missing (->> v (remove #(str/includes? output %)) vec)
        pass?   (empty? missing)]
   [pass? (if pass?
           (str "all substrings present: " v)
           (str "missing substrings: " missing))])

  :contains-or
  ;; Pass if output contains any one of the given substrings (case-insensitive)
  (let [out-lower (str/lower-case output)
        opts      v
        found     (filter #(str/includes? out-lower (str/lower-case %)) opts)
        pass?     (seq found)]
   [pass? (if pass?
           (str "contains one of " opts ": found " found)
           (str "none of " opts " found"))])

  :not-contains
  (let [found (->> v (filter #(str/includes? output %)) vec)
        pass? (empty? found)]
   [pass? (if pass?
           "no forbidden substrings found"
           (str "found forbidden substrings: " found))])

  :regex
  (let [pass? (boolean (re-find v output))]
   [pass? (if pass?
           (str "matches regex " v)
           (str "does not match regex " v))])

  :comparator
  ;; v should be a fn [actual] -> boolean
  (let [pass? (try (v output) (catch Exception _ false))]
   [pass? (str "custom comparator " (if pass? "passed" "failed"))])

  ;; unknown validator
  [false (str "unknown validator " k)]))

(defn validate-text
 "Run an `ollama-fn` f on each test-case. Test-case is a map:
    :in         — input string
    :validators — map of validator-key → condition
  Returns summary:
  {:total   n
   :passed  m
   :failed  k
   :score   {:absolute p/q
              :percent  r}}."
 [f test-cases]
 (let [raw-summary
       (reduce
        (fn [{:keys [total passed failed] :as acc}
             {:keys [in validators]}]
         (let [out     (f in)
               results (map #(check-validator % out) validators)
               pass?   (every? first results)]
          (println "──────────────────────────────────")
          (println "Input:      " in)
          (println "Output:     " out)
          (doseq [[[k _] [ok msg]] (map vector validators results)]
           (println (format " %-12s [%s] %s"
                            (name k)
                            (if ok "OK" "FAIL")
                            msg)))
          (assoc acc
           :total  (inc total)
           :passed (if pass? (inc passed) passed)
           :failed (if pass? failed (inc failed)))))
        {:total  0
         :passed 0
         :failed 0}
        test-cases)
       passed  (:passed raw-summary)
       total   (:total raw-summary)
       percent (if (pos? total)
                (* 100.0 (/ passed total))
                0.0)]
  (assoc raw-summary
   :score {:absolute (str passed "/" total)
           :percent percent})))

