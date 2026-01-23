(ns pyjama.tools.io
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))


(defn list-directory
  "List contents of a directory"
  [{:keys [dir] :or {dir "."}}]
  (let [root (io/file dir)]
    (if (.exists root)
      (let [exclude-set #{".git" ".cpcache" "target" "node_modules" ".DS_Store" ".clj-kondo" ".shadow-cljs" ".lsp" ".agent"}
            files (file-seq root)
            relative-paths (->> files
                                (filter #(.isFile %))
                                (map #(str/replace (.getAbsolutePath %) (str (.getAbsolutePath root) "/") ""))
                                (remove (fn [path]
                                          (let [parts (str/split path #"/")]
                                            (some #(or (exclude-set %)
                                                       (str/starts-with? % "."))
                                                  parts))))
                                sort)]
        {:status :ok
         :files relative-paths
         :count (count relative-paths)
         :text (str "Files in " dir ":\n" (str/join "\n" (take 50 relative-paths))
                    (when (> (count relative-paths) 50)
                      (str "\n... and " (- (count relative-paths) 50) " more.")))})
      {:status :error
       :message (str "Directory not found: " dir)})))

(defn read-files
  "Read contents of specific files"
  [{:keys [files project-dir max-files] :or {project-dir "."}}]
  (let [root (io/file project-dir)
        file-list (cond
                    (string? files) (str/split files #"\s+")
                    (sequential? files) (map (fn [f]
                                               (if (map? f)
                                                 (or (:relative-path f) (:path f) (:file f))
                                                 f))
                                             files)
                    :else [])
        ;; limit files if needed
        target-files (if max-files (take max-files file-list) file-list)
        results (for [path target-files]
                  (let [f (io/file root path)]
                    (if (.exists f)
                      {:path path
                       :content (try (slurp f) (catch Exception e (str "Error reading file: " (.getMessage e))))}
                      {:path path
                       :error "File not found"})))]
    {:status :ok
     :files results
     :text (str/join "\n\n"
                     (for [{:keys [path content error]} results]
                       (if error
                         (str "### " path "\n(File not found)")
                         (str "### " path "\n```\n" content "\n```"))))}))

(defn cat-files
  "Alias for read-files, compatible argument structure"
  [args]
  (read-files args))

(defn write-file
  "Write {{message}} to {{path}}. Creates parent directories if needed. Returns status map."
  [{:keys [path message]}]
  (try
    (let [f (io/file path)]
      (io/make-parents f)
      (spit f message)
      {:status :ok
       :file (.getAbsolutePath f)
       :text (str "Successfully wrote to " path)})
    (catch Exception e
      {:status :error
       :message (str "Failed to write file: " (.getMessage e))})))

