(ns morning.llama-parse-test
  (:require [clojure.test :refer :all]
            [pyjama.llamaparse.core :refer :all]))

(deftest upload-file-test
  (-> "https://www.toyota.com/content/dam/toyota/brochures/pdf/2025/gr86_ebrochure.pdf"
      (parse-file
        {:language                       "ja"
         :premium-mode                   true
         :skip-diagonal-text             true
         :spreadsheet-extract-sub-tables true
         :use-vendor-multimodal-model    true
         :vendor-multimodal-model-name   "anthropic-sonnet-3.5"
         :job-timeout-in-seconds         300
         }
        )
      :id
      println
      ))

(deftest get-job-status-test
  (-> "dc37ec98-ba31-460c-a39b-6f6d4c3b7095"
      get-job-status
      :status
      println))

(deftest get-result-test
  (-> "2a13c81f-62f4-4f20-b387-aef0df998aaa"
      (get-parsing-result :markdown)
      println))

(deftest llama-parser-test
  (llama-parser
    "https://www.toyota.com/content/dam/toyota/brochures/pdf/2025/gr86_ebrochure.pdf"
    {}
    "."))

(deftest old-id
  (wait-and-download "dc37ec98-ba31-460c-a39b-6f6d4c3b7095" "here.md")
  )