(ns morning.validate
 (:require [pyjama.functions :as pj]
           [pyjama.validate :refer :all]
           [clojure.string :as str]
           [clojure.test :refer :all]))

(def bullet-summarizer
 (pyjama.functions/ollama-fn
  {:model  "llama3.2"
   :system "Summarize the following text into exactly 3 concise bullet points."
   :format {:type     "array"
            :minItems 3
            :maxItems 3
            :items    {:type "string" :minLength 10 :maxLength 100}}}))

;; Define some text to summarize
(def sample-text
 "Clojure is a modern Lisp that runs on the JVM. It emphasizes immutability
  and functional programming. It has a vibrant ecosystem and interops seamlessly with Java.")

;; Build test-cases that assert each bullet meets certain text criteria
(def summarizer-tests
 [{:in sample-text
   :validators
   {:min-length 15
    :max-length 80
    :regex      #"^[A-Z].*"
    :contains   ["Java"]}}])

(deftest tryme
 ;; Run validation
 (let [summary (validate-text
                ;; wrap to join array into one text blob for per-case checks
                (fn [txt] (str/join " | " (bullet-summarizer txt)))
                summarizer-tests)]
  (println "\nSummary:")
  (clojure.pprint/pprint summary)))



;; assume birth-info-generator is defined as before:
(def birth-info-generator
 (pyjama.functions/ollama-fn
  {:model  "llama3.2"
   :system "Given a birth date, determine the Western zodiac sign, the Chinese zodiac sign, and provide a 1–2 sentence personality summary based on those signs."
   :format {:type       "object"
            :required   [:westernZodiac :chineseZodiac :personality]
            :properties {:westernZodiac {:type "string"}
                         :chineseZodiac {:type "string"}
                         :personality   {:type "string" :minLength 20}}}}))

;; Wrappers to extract each field as a string
(def western-zodiac
 (fn [date]
  (:westernZodiac (birth-info-generator date))))

(def chinese-zodiac
 (fn [date]
  (:chineseZodiac (birth-info-generator date))))

(def personality
 (fn [date]
  (:personality (birth-info-generator date))))

(deftest birth-info-validation-test
 ;; Validate Western Zodiac
 (let [res-west (validate-text
                 western-zodiac
                 [{:in         "May 22, 1979"
                   :validators {:contains   ["Gemini"]
                                :min-length 3
                                :max-length 10}}])
       ;; Validate Chinese Zodiac
       res-chin (validate-text
                 chinese-zodiac
                 [{:in         "May 22, 1979"
                   :validators {:contains   ["Sheep"]
                                :min-length 3
                                :max-length 10}}])
       ;; Validate Personality blurb
       res-pers (validate-text
                 personality
                 [{:in         "May 22, 1979"
                   :validators {:min-length 20
                                :contains   ["adaptable" "curious"]}}])]
  (println "\nWestern Zodiac summary:" res-west)
  (println "Chinese Zodiac summary:" res-chin)
  (println "Personality summary:" res-pers)
  ;; And assert all passed at least one test-case
  (is (= 0 (:failed res-west)))
  (is (= 0 (:failed res-chin)))
  (is (= 0 (:failed res-pers)))))



;; reuse the same generator definition
(def hex-color-generator
 (pyjama.functions/ollama-fn
  {:model  "llama3.2"
   :system "Generate a single random hex color code (e.g. #1A2B3C)."
   :format {:type    "string"
            :pattern "^#[0-9A-Fa-f]{6}$"}}))

(deftest hex-color-validation-test
 (let [summary
       (validate-text
        hex-color-generator
        [{:in         "light blue"                          ;; no input needed
          :validators {:regex      #"^#[0-9A-Fa-f]{6}$"
                       :comparator (fn [out]
                                    (let [n (Integer/parseInt (subs out 1) 16)]
                                     (<= 0 n 0xFFFFFF)))}}])]
  (println "\nHex Color summary:" summary)
  (is (= 0 (:failed summary)))))

;; Reuse your prime-checker definition
(def prime-checker
 (pyjama.functions/ollama-fn
  {:model  "llama3.1"
   :system "Reply ONLY true or false: is the given positive integer prime?"
   :format {:type "boolean"}}))

(deftest prime-checker-validation-test
 (let [summary
       (validate-text
        prime-checker
        [{:in "13" :expected true}
         {:in "100" :expected false}
         {:in "7" :expected true}
         {:in "10" :expected false}])]
  (println "\nPrime Checker summary:" summary)
  (is (= 0 (:failed summary)))))



(def color-mixer
 (pj/ollama-fn
  {:model  "llama3.2"
   :system "Given two primary colors separated by 'and', return the resulting color name (e.g. red and yellow → orange)."
   :format {:type "string"}}))

(deftest color-mixer-validation-test
 (let [test-cases
       [{:in         "red and yellow"
         :validators {:regex       #"(?i)^orange$"
                      :contains-or ["Orange"]}}
        {:in         "blue and yellow"
         :validators {;:regex         #"(?i)^green$"
                      :contains-or ["green"]}}
        {:in         "red and blue"
         :validators {:contains-or ["purple" "magenta"]}}]
       summary (validate-text color-mixer test-cases)]
  (println "\nColor Mixer summary:" summary)
  (is (= 0 (:failed summary)))))


(def email-extractor
 (pj/ollama-fn
  {:model  "llama3.2"
   :system "Extract all email addresses from the text; return as an array of strings."
   :format {:type     "array"
            :minItems 0
            :items    {:type "string" :format "email"}}}))

(deftest email-extractor-validation-test
 (let [test-cases
       [{:in         "Contact us at foo@example.com or bar.baz@domain.co for info."
         :expected   ["foo@example.com" "bar.baz@domain.co"]
         :comparator (fn [actual expected]
                      (and (sequential? actual)
                           (= (set actual) (set expected))))}
        {:in       "No emails here!"
         :expected []}]
       summary (validate-text email-extractor test-cases)]
  (println "\nEmail Extractor summary:" summary)
  (is (= 0 (:failed summary)))))



;; 1. URL Extractor
(def url-extractor
 (pj/ollama-fn
  {:model  "llama3.2"
   :system "Extract all URLs from the text; return as an array of strings."
   :format {:type  "array"
            :items {:type "string" :format "uri"}}}))

(deftest url-extractor-validation-test
 (let [test-cases
       [{:in         "Visit http://example.com and https://sub.domain.co/path?arg=1#frag!"
         :expected   ["http://example.com" "https://sub.domain.co/path?arg=1#frag"]
         :comparator (fn [actual expected]
                      (= (set actual) (set expected)))}
        {:in       "No links here"
         :expected []}]
       summary (validate-text url-extractor test-cases)]
  (println "\nURL Extractor summary:" summary)
  (is (= 0 (:failed summary)))))

;; 2. Language Detector
(def lang-detector
 (pj/ollama-fn
  {:model  "llama3.2"
   :system "Detect the primary language of the sentence; return ISO code."
   :format {:type "string" :enum ["en" "ja" "es" "fr" "de" "zh" "ko"]}}))

(declare summary)
(deftest language-detector-validation-test
 (let [summary
       (validate-text lang-detector
                      [{:in         "Hello, world!"
                        :validators {:regex #"(?i)^en$"}}
                       {:in         "こんにちは"
                        :validators {:regex #"(?i)^ja$"}}
                       {:in         "Hola, ¿cómo estás?"
                        :validators {:regex #"(?i)^es$"}}])]
  (println "\nLanguage Detector summary:" summary)
  (is (= 0 (:failed summary)))))


;; reuse your unit-converter ollama-fn
(def unit-converter
 (pj/ollama-fn
  {:model  "llama3.2"
   :system "Convert the given amount and unit to the target unit, returning an object."
   :format {:type       "object"
            :required   [:value :unit]
            :properties {:value   {:type "number"}
                         :unit    {:type "string" :pattern "^[a-zA-Z]+$"}}}}))

(deftest unit-converter-validation-test
 (let [summary
       (validate-text
        unit-converter
        [{:in "5 km to miles"
          :validators
          {:comparator
           ;; actual is the map returned by ollama-fn
           (fn [actual]
            (and
             ;; unit must be "miles"
             (= "miles" (:unit actual))
             ;; value within 0.001 of expected 3.10686
             (< (Math/abs (- (:value actual) 3.10686)) 0.001)))}}])]
  (println "\nUnit Converter summary:" summary)
  (is (= 0 (:failed summary)))))


;; 4. Markdown Table Parser
(def table-parser
 (pj/ollama-fn
  {:model  "llama3.2"
   :system "Parse the following Markdown table into an array of row objects."
   :format {:type  "array"
            :items {:type       "object"
                    :properties {"Name" {:type "string"}
                                 "Age"  {:type "string"}
                                 "City" {:type "string"}}}}}))

(deftest table-parser-validation-test
 (let [md "| Name  | Age | City      |\n|-------|-----|-----------|\n| Alice | 30  | Tokyo     |\n| Bob   | 25  | San Diego |"
       expected [{"Name" "Alice" "Age" "30" "City" "Tokyo"}
                 {"Name" "Bob" "Age" "25" "City" "San Diego"}]
       summary (validate-text table-parser
                              [{:in         md
                                :expected   expected
                                :comparator (fn [actual expected]
                                             (= actual expected))}])]
  (println "\nTable Parser summary:" summary)
  (is (= 0 (:failed summary)))))

;; 5. Entity Linker
(def entity-linker
 (pj/ollama-fn
  {:model  "llama3.2"
   :system "Identify named entities in text; return array of {text,type,url}."
   :format {:type  "array"
            :items {:type       "object"
                    :required   ["text" "type" "url"]
                    :properties {"text" {:type "string"}
                                 "type" {:type "string" :enum ["Person" "Location" "Organization"]}
                                 "url"  {:type "string" :format "uri"}}}}}))

(deftest entity-linker-validation-test
 (let [text "Apple released a new product in Cupertino."
       summary (validate-text entity-linker
                              [{:in         text
                                :expected   nil
                                :comparator (fn [actual _]
                                             (and (some #(and (= (get % "text") "Apple")
                                                              (= (get % "type") "Organization")) actual)
                                                  (some #(and (= (get % "text") "Cupertino")
                                                              (= (get % "type") "Location")) actual)))}])]
  (println "\nEntity Linker summary:" summary)
  (is (= 0 (:failed summary)))))

;; 6. Password Strength Scorer
(def password-scorer
 (pj/ollama-fn
  {:model  "llama3.2"
   :system "Score password strength (0-100) and give feedback tips."
   :format {:type       "object"
            :required   [:score :feedback]
            :properties {:score    {:type "integer" :minimum 0 :maximum 100}
                         :feedback {:type "array" :items {:type "string"}}}}}))

(deftest password-scorer-validation-test
 (let [summary
       (validate-text
        password-scorer
        [{:in "password"
          :validators
          {:comparator
           (fn [{:keys [score feedback]}]
            ;; weak password: score under 30 and non-empty feedback
            (and (< score 30)
                 (sequential? feedback)
                 (not (empty? feedback))))}}

          {:in "P@ssw0rd!2025"
           :validators
           {:comparator
            (fn [{:keys [score feedback]}]
             ;; strong password: score over 70 and feedback is a seq
             (and (> score 70)
                  (sequential? feedback)))}}])]
  (println "\nPassword Scorer summary:" summary)
  ;; We expect 1 failure (the second case), so :failed should be 1:
  (is (= 1 (:failed summary)))
  (is (= 2 (:total summary)))
  (is (= 1 (:passed summary)))
  (is (= "1/2" (-> summary :score :absolute))) ;; or inspect :score map directly
  ))


;; 1. Define the chess move proposer
(def chess-move-generator
 (pj/ollama-fn
  {:model  "llama3.2"
   :system "Given the moves played so far in PGN (without result), propose the next best move in standard algebraic notation."
   :format {:type    "string"
            :pattern "^[KQRNB]?[a-h]?x?[a-h][1-8](=[QRNB])?)[+#]?$"}}))

(deftest chess-move-validation-test
 (let [pgn     "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6"
       summary (validate-text
                chess-move-generator
                [{:in pgn
                  :validators
                  {:regex #"^(O-O(-O)?|[KQRNB]?[a-h]?[1-8]?x?[a-h][1-8](=[QRNB])?)[+#]?$"}}])]
  (println "\nChess Move Generator summary:" summary)
  (is (= 0 (:failed summary)))))

