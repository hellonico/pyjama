(ns morning.ollama-functions-extra-test
 (:require [clojure.test :refer :all]
           [pyjama.functions :refer :all]))

(deftest translation-test
 (println (en-jp-translator "Innovation distinguishes between a leader and a follower."))
 (is (string? (en-jp-translator "Hello, world!"))))

(deftest date-parser-test
 (let [d1 (date-parser "July 20, 1969")
       d2 (date-parser "2025-12-31")]
  (println d1)                                              ;; e.g. {"year":1969,"month":7,"day":20}
  (println d2)
  (is (= 1969 (:year d1)))
  (is (= 12 (:month d2)))))

(deftest prime-checker-test
 (is (true? (prime-checker "13")))
 (is (false? (prime-checker "100")))
 (println "7 →" (prime-checker "7") ", 10 →" (prime-checker "10")))

(deftest password-generator-test
 (let [pw (password-generator "Create a password")]
  (println "Generated:" pw)
  ;; basic regex check in Clojure
  (is (re-matches #"^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,}$" pw))))

(deftest weather-forecast-test
 (let [fc (weather-forecast "")]
  (println fc)
  (is (= 3 (count fc)))
  (doseq [day fc]
   (is (string? (:day day)))
   (is (integer? (:temp day))))))

(deftest quiz-generator-test
 (let [{:keys [question options answer]} (quiz-generator "")]
  (println "Q:" question)
  (println "Options:" options)
  (println "Answer:" answer)
  (is (>= (count options) 3))
  (is (some #(= % answer) options))))

(deftest sentiment-analyzer-test
 (let [{:keys [sentiment score explanation]}
       (sentiment-analyzer "I love using open source libraries!")]
  (println sentiment score explanation)
  (is (#{"positive" "neutral" "negative"} sentiment))
  (is (<= 0 score 100))
  (is (string? explanation))))

(deftest book-list-test
 (let [books (book-list-generator "")]
  (println books)
  (is (= 2 (count books)))
  (doseq [{:keys [title authors]} books]
   (is (string? title))
   (is (sequential? authors)
       (str "Authors should be an array, got: " (type authors))))))

(deftest birth-info-test
 (let [{:keys [westernZodiac chineseZodiac personality]}
       (birth-info-generator "June 7th, 2005")]
  (println "Western Zodiac:" westernZodiac)
  (println "Chinese Zodiac:" chineseZodiac)
  (println "Personality:" personality)
  (is (string? westernZodiac))
  (is (string? chineseZodiac))
  (is (>= (count personality) 20))))

(deftest random-number-test
 (let [n (random-number-generator "1 and 100")]
  (println "Random number:" n)
  (is (integer? n))
  (is (<= 1 n 100))))

(deftest age-calculator-test
 (let [age (age-calculator "June 13, 1995")]
  (println "Age:" age)
  (is (integer? age))
  (is (>= age 0))))

(deftest duration-parser-test
 (let [{:keys [hours minutes]} (duration-parser "2h 45m")]
  (println hours "hours and" minutes "minutes")
  (is (integer? hours))
  (is (integer? minutes))
  (is (< minutes 60))))

(deftest currency-converter-test
 (let [{:keys [amount currency]} (currency-converter "100 USD to EUR")]
  (println amount currency)
  (is (number? amount))
  (is (re-matches #"[A-Z]{3}" currency))))

(deftest email-extractor-test
 (let [txt "Contact us at foo@example.com or bar.baz@domain.co."
       emails (email-extractor txt)]
  (println "Emails:" emails)
  (is (sequential? emails))
  (doseq [e emails] (is (re-matches #".+@.+\..+" e)))))

(deftest hex-color-test
 (let [col (hex-color-generator "light blue")]
  (println "Hex color:" col)
  (is (re-matches #"^#[0-9A-Fa-f]{6}$" col))))

(deftest bullet-summarizer-test
 (let [text  "Clojure is a modern Lisp that runs on the JVM. It emphasizes immutability and functional programming. It has a vibrant ecosystem and interops seamlessly with Java."
       pts   (bullet-summarizer text)]
  (println "Summary:" pts)
  (is (= 3 (count pts)))
  (doseq [p pts] (is (string? p)))))


;
;
