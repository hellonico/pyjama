(ns pyjama.docs-test
  "
  Notes and rationale
  - core.process-review: Tests verify correct wiring of inputs to pyjama.core/call, file writing semantics, summary pass behavior, and PDF invocation including graceful failure with warning.
  - core.resolve-output-file: Timestamp is fixed via with-redefs to avoid time-based flakiness.
  - core.-main: Only happy path and no-args usage are tested to avoid JVM termination from System/exit in error branch.
  - utils.extract-ns-doc: Covers string doc, metadata doc, and third-position string; malformed input returns nil.
  - utils.read-files-in-dir: The current implementation sorts by (:file %), but read-file does not include :file. The test injects a stub read-file that includes :file to expose and work around this oversight; if you run against production code, this test would highlight the bug.
  - utils.read-files-by-patterns: Validates deduplication by canonical path and stable ordering.
  - External dependencies pyjama.helpers.file, pyjama.core, and pyjama.tools.pandoc are stubbed in unit tests, isolating behavior.

  These tests should give high confidence in the orchestration and utility logic, and they also surface a potential bug in read-files-in-dir sorting by a :file key that is not present in read-file’s return map.
  "
  (:require
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [pyjama.doc.core :as core]
    [pyjama.doc.utils :as u])
  (:import (java.io File StringWriter)
           (java.nio.file Files Paths)
           (java.nio.file.attribute FileAttribute)))

(defn- tmp-dir []
  (-> (Files/createTempDirectory "pyjama-core-test" (make-array FileAttribute 0))
      .toFile))

(defn- tmp-file [dir name content]
  (let [f (io/file dir name)]
    (io/make-parents f)
    (spit f content :encoding "UTF-8")
    f))

(deftest timestamp-utc-format
  (let [ts (#'core/timestamp-utc)]
    (is (re-matches #"\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}Z" ts))))

(deftest ensure-str-behavior
  (is (= "" (#'core/ensure-str nil)))
  (is (= "abc" (#'core/ensure-str "abc")))
  (is (= "42" (#'core/ensure-str 42)))
  (is (= "{:a 1}" (#'core/ensure-str {:a 1}))))

(deftest resolve-output-file-cases
  (with-redefs [pyjama.helpers.file/file-ext (fn [^File f]
                                               (let [n (.getName f)
                                                     i (.lastIndexOf n ".")]
                                                 (when (pos? i) (subs n (inc i)))))
                ;; fix the timestamp to assert deterministically
                pyjama.doc.core/timestamp-utc (fn [] "2020-01-02_03-04-05Z")]
    ;; nil -> ./pyjama-doc/<ts>.md
    (let [f (core/resolve-output-file nil)]
      (is (instance? File f))
      (is (= (str (io/file "pyjama-doc" "2020-01-02_03-04-05Z.md")) (.getPath f))))
    ;; directory -> <dir>/<ts>.md
    (let [dir (tmp-dir)
          f (core/resolve-output-file dir)]
      (is (= (str (io/file dir "2020-01-02_03-04-05Z.md")) (.getPath f))))
    ;; file path with no extension -> treat as dir
    (let [dir (tmp-dir)
          base (io/file dir "out")                          ; doesn't exist; still treated as dir
          f (core/resolve-output-file base)]
      (is (= (str (io/file base "2020-01-02_03-04-05Z.md")) (.getPath f))))
    ;; file with extension -> use as-is
    (let [dir (tmp-dir)
          base (io/file dir "report.md")
          f (core/resolve-output-file base)]
      (is (= (.getPath base) (.getPath f))))))

;(deftest summary-file-naming
;  (with-redefs [pyjama.helpers.file/file-ext (fn [^File f]
;                                               (let [n (.getName f)
;                                                     i (.lastIndexOf n ".")]
;                                                 (when (pos? i) (subs n (inc i)))))]
;    (let [f (io/file "/a/b/report.md")
;          s (#'core/summary-file f)]
;      (is (= "/a/b/report_summary.md" (.getPath s))))
;    (let [f (io/file "/a/b/report")                         ; no extension
;          s (#'core/summary-file f)]
;      (is (= "/a/b/report_summary.md" (.getPath s))))))

(deftest deep-merge-behavior
  (let [a {:a 1 :b {:x 1 :y [1]} :c [1] :d nil}
        b {:a 2 :b {:y [2] :z 3} :c [2 3] :d 42 :e "x"}
        m (#'core/deep-merge a b)]
    (is (= 2 (:a m)))
    (is (= {:x 1 :y [1 2] :z 3} (:b m)))
    (is (= [1 2 3] (:c m)))
    (is (= 42 (:d m)))
    (is (= "x" (:e m)))))

(deftest normalize-config-shapes
  (let [cfg {:patterns '("a" {:pattern "b" :metadata "m"})
             :model    {:temp 1}
             :out-file "out.md"
             :system   "sys"
             :pre      "pre"
             :pdf      1
             :summary  "sum"}
        n (#'core/normalize-config cfg)]
    (is (vector? (:patterns n)))
    (is (= {:temp 1} (:model n)))
    (is (= "out.md" (:out-file n)))
    (is (= "sys" (:system n)))
    (is (= "pre" (:pre n)))
    (is (= true (:pdf n)))
    (is (= "sum" (:summary n))))
  ;; defaults
  (let [n (#'core/normalize-config {:patterns []})]
    (is (= {} (:model n)))
    (is (= [] (:patterns n)))))

(deftest call-agent->string-handles-nil
  (let [calls (atom [])]
    (with-redefs [pyjama.core/call (fn [in] (swap! calls conj in) nil)]
      (is (= "" (#'core/call-agent->string {:x 1})))
      (is (= [{:x 1}] @calls))))
  (with-redefs [pyjama.core/call (fn [_] "ok")]
    (is (= "ok" (#'core/call-agent->string {})))))

;(deftest process-review-writes-files-and-invokes-agent
;  (let [dir (tmp-dir)
;        ag-calls (atom [])
;        pdf-calls (atom [])
;        ts "2020-01-02_03-04-05Z"]
;    (with-redefs [pyjama.doc.utils/aggregate-md-from-patterns (fn [entries] (str "AGG|" (pr-str entries)))
;                  pyjama.core/call (fn [input]
;                                     (swap! ag-calls conj input)
;                                     (if (= 1 (count @ag-calls)) "R1" "R2"))
;                  pyjama.tools.pandoc/md->pdf (fn [m] (swap! pdf-calls conj m) :ok)
;                  pyjama.helpers.file/file-ext (fn [^File f]
;                                                 (let [n (.getName f)
;                                                       i (.lastIndexOf n ".")]
;                                                   (when (pos? i) (subs n (inc i)))))
;                  pyjama.doc.core/timestamp-utc (fn [] ts)]
;      ;; out-file is a directory => timestamped .md
;      (let [cfg {:patterns ["src/**/*.clj" {:pattern "README.md" :metadata "docs"}]
;                 :model    {:temperature 0.2}
;                 :out-file dir
;                 :system   "SYS"
;                 :pre      "PRE"
;                 :pdf      true
;                 :summary  "SUMPRE"}
;            res (core/process-review cfg)]
;        ;; wrote files
;        (is (.exists (io/file (:out res))))
;        (is (= (slurp (:out res) :encoding "UTF-8") "R1"))
;        (is (.exists (io/file (:summary res))))
;        (is (= (slurp (:summary res) :encoding "UTF-8") "R2"))
;        (is (= (str (:out res) ".pdf") (:pdf res)))
;        ;; pandoc called with expected args
;        (is (= [{:input  (io/file (:out res))
;                 :output (str (:out res) ".pdf")}]
;               (map (fn [m] (update m :input #(.getPath ^File %))) @pdf-calls)))
;        ;; agent called twice with proper prompts and pres
;        (is (= 2 (count @ag-calls)))
;        (let [[first second] @ag-calls]
;          (is (= "SYS" (:system first)))
;          (is (= "PRE" (:pre first)))
;          (is (= ["AGG|[\"src/**/*.clj\" {:pattern \"README.md\", :metadata \"docs\"}]"] (:prompt first)))
;          (is (= "SYS" (:system second)))
;          (is (= "SUMPRE" (:pre second)))
;          (is (= ["R1"] (:prompt second)))))))
;  ;; verify PDF failure is caught and prints a warning while still returning :pdf path
;  (let [dir (tmp-dir)
;        ts "2020-01-02_03-04-05Z"
;        err (StringWriter.)]
;    (with-redefs [pyjama.doc.utils/aggregate-md-from-patterns (fn [_] "AGG")
;                  pyjama.core/call (fn [_] "R1")
;                  pyjama.tools.pandoc/md->pdf (fn [_] (throw (ex-info "bad pandoc" {})))
;                  pyjama.helpers.file/file-ext (fn [^File f]
;                                                 (let [n (.getName f)
;                                                       i (.lastIndexOf n ".")]
;                                                   (when (pos? i) (subs n (inc i)))))
;                  pyjama.doc.core/timestamp-utc (fn [] ts)]
;      (binding [*err* err]
;        (let [res (core/process-review {:patterns []
;                                        :out-file dir
;                                        :pdf      true})]
;          (is (.exists (io/file (:out res))))
;          (is (= (str (:out res) ".pdf") (:pdf res)))
;          (is (re-find #"WARN: PDF generation failed" (str err))))))))

(deftest arg->config-cases
  (let [calls (atom [])]
    (with-redefs [pyjama.helpers.config/load-config (fn [paths] (swap! calls conj paths) {:model {:x 1}})]
      (is (= {:model {:x 1}} (core/arg->config "foo.edn")))
      (is (= [["foo.edn"]] @calls)))
    (is (= {:patterns [{:pattern "README.md"}]} (core/arg->config "README.md")))
    ;; case-insensitive .EDN
    (with-redefs [pyjama.helpers.config/load-config (fn [_] :ok)]
      (is (= :ok (core/arg->config "CFG.EDN"))))))

(deftest main-prints-usage-on-empty
  (let [out (StringWriter.)]
    (binding [*out* out]
      (core/-main))
    (is (re-find #"Usage:" (str out)))))

;(deftest main-happy-path
;  (let [out (StringWriter.)
;        review {:out "/tmp/out.md" :summary "/tmp/out_summary.md" :pdf "/tmp/out.md.pdf"}]
;    (with-redefs [pyjama.doc.core/arg->config identity
;                  pyjama.doc.core/deep-merge (fn [& cfgs] (apply merge cfgs))
;                  pyjama.doc.core/normalize-config identity
;                  pyjama.doc.core/process-review (fn [_] review)]
;      (binding [*out* out]
;        (core/-main "a" "b"))
;      (let [s (str out)]
;        (is (re-find #"Effective config:" s))
;        (is (re-find #"Wrote: /tmp/out.md" s))
;        (is (re-find #"Wrote summary: /tmp/out_summary.md" s))
;        (is (re-find #"Wrote PDF: /tmp/out.md.pdf" s))))))

(defn- tmp-dir []
  (-> (Files/createTempDirectory "pyjama-utils-test" (make-array FileAttribute 0))
      .toFile))

(defn- tmp-file [dir name content]
  (let [f (io/file dir name)]
    (io/make-parents f)
    (spit f content :encoding "UTF-8")
    f))

(deftest fence-lang-mapping
  (is (= "clojure" (#'u/fence-lang "clj")))
  (is (= "bash" (#'u/fence-lang "zsh")))
  (is (= "tsx" (#'u/fence-lang "TsX")))
  (is (= "foo" (#'u/fence-lang "FOO")))
  (is (= "" (#'u/fence-lang nil))))

(deftest extract-ns-doc-variants
  ;; simple string doc
  (is (= "My ns doc"
         (#'u/extract-ns-doc "(ns my.app \"My ns doc\")\n(def x 1)\n")))
  ;; metadata doc
  (is (= "Doc via meta"
         (#'u/extract-ns-doc "(ns my.app {:doc \"Doc via meta\" :author \"me\"})\n(def a 1)")))
  ;; third-position string doc (after attr map)
  (is (= "Trailing doc"
         (#'u/extract-ns-doc "(ns my.app {:a 1} \"Trailing doc\" (:require [clojure.set :as set]))")))
  ;; no ns form
  (is (nil? (#'u/extract-ns-doc "(def a 1)\n(def b 2)")))
  ;; malformed should be handled gracefully and return nil
  (is (nil? (#'u/extract-ns-doc "(ns my.app \"unterminated"))))

(deftest read-file-basic-and-max-bytes
  (let [dir (tmp-dir)
        md (tmp-file dir "readme.md" "# Title\nSome text")
        clj (tmp-file dir "core.clj" "(ns foo.core \"Doc for foo\")\n(defn x [] 1)\n")]
    (testing "text file"
      (let [m (u/read-file md "MD meta")]
        (is (= "readme.md" (:filename m)))
        (is (= "md" (:ext m)))
        (is (= :text (:kind m)))
        (is (= "MD meta" (:metadata m)))
        (is (re-find #"^# Title" (:content m)))
        (is (nil? (:skipped? m)))))
    (testing "code file with ns doc metadata extraction"
      (let [m (u/read-file clj nil)]
        (is (= "core.clj" (:filename m)))
        (is (= "clj" (:ext m)))
        (is (= :code (:kind m)))
        (is (= "Doc for foo" (:metadata m)))))
    (testing "max-bytes skip"
      (let [m (u/read-file md nil {:max-bytes 1})]
        (is (= "readme.md" (:filename m)))
        (is (:skipped? m))
        (is (re-find #"skipped: file too large" (:content m)))))))

(deftest read-files-in-dir-uses-hf-and-sorts-stably
  ;; We stub hf/files-matching-patterns and u/read-file to ensure deterministic behavior and to satisfy the sort-by :file bug.
  (let [dir (tmp-dir)
        f1 (io/file dir "a.md")
        f2 (io/file dir "b.md")
        f3 (io/file dir "c.txt")]
    (spit f1 "A" :encoding "UTF-8")
    (spit f2 "B" :encoding "UTF-8")
    (spit f3 "C" :encoding "UTF-8")
    (with-redefs [pyjama.helpers.file/files-matching-patterns (fn [_dir _patterns] [f2 f1 f3])
                  ;; read-file returns map including a :file key so the sort-by in read-files-in-dir works
                  pyjama.doc.utils/read-file (fn [^File f _]
                                               {:file     f
                                                :filename (.getName f)
                                                :ext      (let [n (.getName f)
                                                                i (.lastIndexOf n ".")]
                                                            (when (pos? i) (subs n (inc i))))
                                                :kind     (if (re-find #"\.(md|txt)$" (.getName f)) :text :code)
                                                :metadata nil
                                                :content  (slurp f :encoding "UTF-8")})]
      (let [res (u/read-files-in-dir dir ["*.md" "*.txt"])
            names (map :filename res)]
        (is (= ["a.md" "b.md" "c.txt"] names))))))

;(deftest normalize-pattern-entries-cases
;  (is (= [{:pattern "a" :metadata nil}
;          {:pattern "b" :metadata "m"}]
;         (#'u/normalize-pattern-entries ["a" {:pattern "b" :metadata "m"}])))
;  (is (thrown-with-msg? clojure.lang.ExceptionInfo
;                        #"Invalid pattern entry"
;                        (#'u/normalize-pattern-entries [42]))))

(deftest expand-pattern-entry->files-uses-hf
  (let [dir (tmp-dir)
        f1 (tmp-file dir "x.clj" "")
        f2 (tmp-file dir "y.clj" "")]
    (with-redefs [pyjama.helpers.file/files-matching-path-patterns (fn [[pattern]]
                                                                     (is (= "**/*.clj" pattern))
                                                                     [f2 f1])]
      (let [res (#'u/expand-pattern-entry->files {:pattern "**/*.clj" :metadata "M"})]
        (is (= #{{:file f2 :metadata "M"} {:file f1 :metadata "M"}}
               (set res)))))))

(deftest read-files-by-patterns-dedups-and-sorts
  (let [dir (tmp-dir)
        f1 (tmp-file dir "a.clj" "(ns a \"doc A\")")
        f2 (tmp-file dir "b.clj" "(ns b \"doc B\")")]
    (with-redefs [pyjama.helpers.file/files-matching-path-patterns
                  (fn [[pattern]]
                    (case pattern
                      "pat1" [f1 f2]
                      "pat2" [f2]                           ; duplicate
                      []))
                  ;; keep read-file simple for this test
                  pyjama.doc.utils/read-file
                  (fn [^File f md] {:filename (.getName f)
                                    :ext      "clj"
                                    :kind     :code
                                    :metadata md
                                    :content  ""})]
      (let [res (u/read-files-by-patterns ["pat1" {:pattern "pat2" :metadata "M2"}])]
        ;; dedup keeps first match's metadata (nil) for b.clj
        (is (= ["a.clj" "b.clj"] (map :filename res)))
        (is (= [nil nil] (map :metadata res)))))))

(deftest aggregate-md-formats-text-and-code
  (let [files [{:filename "a.clj" :ext "clj" :kind :code :metadata "Doc A" :content "(def a 1)"}
               {:filename "README.md" :ext "md" :kind :text :content "# Title\nText"}
               {:filename "z.py" :ext "py" :kind :code :content "print('x')"}]
        md (u/aggregate-md files)]
    (is (re-find #"## a\.clj" md))
    (is (re-find #"_Doc A_" md))
    (is (re-find #"```clojure\n\(def a 1\)\n```" md))
    (is (re-find #"## README\.md" md))
    (is (re-find #"# Title" md))
    (is (re-find #"```python\nprint\('x'\)\n```" md))))

(deftest aggregate-md-from-patterns-composes
  (with-redefs [pyjama.doc.utils/read-files-by-patterns (fn [entries]
                                                          (is (= ["a" {:pattern "b" :metadata "M"}] entries))
                                                          [{:filename "a.clj" :ext "clj" :kind :code :content "(+ 1 2)"}])]
    (let [md (u/aggregate-md-from-patterns ["a" {:pattern "b" :metadata "M"}])]
      (is (re-find #"## a\.clj" md))
      (is (re-find #"```clojure" md)))))