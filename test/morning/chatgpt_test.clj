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
