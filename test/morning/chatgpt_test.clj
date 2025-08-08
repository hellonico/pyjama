(ns morning.chatgpt-test
  (:require
    [clojure.test :refer :all]
    [pyjama.chatgpt.core :as gpt]))


(deftest gpt-call
  (println
    (gpt/chatgpt
      {:prompt "give me a command to show number of sata ports linux"})))

(deftest gpt-call-streaming
  (println
    (gpt/chatgpt
      {:streaming true :prompt "give me a command to show number of sata ports linux"})))

(deftest llama-swap
  (println
    (gpt/chatgpt
      {:url "http://localhost:9292/v1" :model "smollm2" :prompt "give me a command to show number of sata ports linux"})))

(deftest llama-swap-streaming
  (println
    (gpt/chatgpt
      {:url "http://localhost:9292/v1" :model "smollm2" :streaming true :prompt "give me a command to show number of sata ports linux"})))

(deftest image-vision-streaming-test
  (println
    (gpt/chatgpt
      {:model "gpt-4o"
       :streaming true
       :prompt "What is shown in this image?"
       :image-path "resources/cute_cat.jpg"})))

;
;
(deftest gpt-4-5-preview
  (->
    (gpt/chatgpt
      {:model  "gpt-4.5-preview"
       :prompt "give me a command to show number of sata ports linux"})
    println))

(deftest gpt-4-5-preview-analysis
  (println
    (gpt/chatgpt
      {:model  "gpt-4.5-preview"
       :prompt "I asked you a question earlier:

       'give me a command to show number of sata ports linux'

       And that was your answer:

       '
       You can use the following command on Linux to display the number of SATA ports detected by your system:\n\n```bash\ndmesg | grep -i sata | grep 'link up' | wc -l\n```\n\n**Explanation:**\n- `dmesg` displays kernel ring buffer messages.\n- `grep -i sata` filters messages related to SATA (case-insensitive).\n- `grep 'link up'` filters only SATA ports that are currently active (connected devices).\n- `wc -l` counts the number of lines, giving you the total number of SATA ports with active connections.\n\nAlternatively, you can list all SATA devices and their corresponding ports using:\n\n```bash\nlsscsi | grep -i sata\n```\n\nor\n\n```bash\nlsblk -o NAME,TYPE,TRAN | grep sata\n```\n\nThe above commands will help you identify SATA devices currently recognized by Linux.
       '

       Analyse your answer, and propose ways you could make it better.

       "})))

;
;
;

(def models
  ;; Core GPT-5 family
  ["gpt-5"
   "gpt-5-mini"
   "gpt-5-nano"])
;
;(def chat-router
;  ;; Optional: Chat-tuned router model that maps to GPT-5 under the hood.
;  ["gpt-5-chat-latest"])

(def cat-img "resources/cute_cat.jpg")

(defn ^:private ask-vision
  [model]
  (gpt/chatgpt {:model model
                ;:streaming true
                :prompt "What is shown in this image?"
                :image-path cat-img}))

(defn ^:private cat? [s]
  (boolean (re-find #"(?i)\b(cat|kitten)\b" (str s))))

(deftest gpt5-vision-streaming-tests
  (doseq [m models]
    (testing (str "Vision streaming with " m)
      (let [out (ask-vision m)]
        (println "\n[" m "] OUTPUT:\n" out)
        (is (string? (str out)) (str m " should return stringifiable output"))
        (is (seq (str out)) (str m " should return non-empty output"))
        (is (cat? out) (str m " should recognize a cat in the image"))))))

(deftest gpt5-chat-latest-vision-streaming-test
  (testing "Vision streaming with gpt-5-chat-latest"
    (let [m "gpt-5-chat-latest"
          out (ask-vision m)]
      (println "\n[" m "] OUTPUT:\n" out)
      (is (string? (str out)) "should return stringifiable output")
      (is (seq (str out)) "should return non-empty output")
      (is (cat? out) "should recognize a cat in the image"))))
