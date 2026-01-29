(ns pyjama.tools.template
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn find-template
  "Find template file from multiple locations (user folders, then built-in resources)"
  [template-name dir]
  (let [template-filename (if (str/ends-with? template-name ".md") template-name (str template-name ".md"))
        user-home (System/getProperty "user.home")
        ;; Check these locations in order
        locations [(str user-home "/.codebase-analyzer/templates/" template-filename)
                   (str dir "/user-templates/" template-filename)
                   (str "analysis-templates/" template-filename)]]

    ;; Try file system locations first
    (or (some (fn [path]
                (let [f (io/file path)]
                  (when (.exists f)
                    {:source :file :path path :content (slurp f)})))
              (take 2 locations))

        ;; Then try classpath resource
        (when-let [resource (io/resource (last locations))]
          {:source :resource :path (last locations) :content (slurp resource)}))))

(defn render-custom-template
  "Render a markdown template by replacing {{context}} with provided context"
  [{:keys [template context project-dir] :or {project-dir "."}}]
  (if-let [{:keys [content path]} (find-template template project-dir)]
    {:status :ok
     :template path
     :text (str/replace content "{{context}}" (or context ""))}
    {:status :error
     :message (str "Template not found: " template)}))

(defn inject-template-context
  "Inject codebase context into a template string (template content, not name)
   Replaces:
   - {{context}} with combined codebase files and git history
   - {{codebase-files}} with just the codebase files
   - {{git-history}} with just the git history
   - {{project-dir}} with the project directory
   
   Template can be:
   - Direct string content
   - file:// reference (e.g., file:///path/to/template.md)"
  [{:keys [template codebase-files git-history project-dir ctx]
    :or {codebase-files "" git-history "" project-dir "."}}]
  (let [;; Resolve file:// references
        template-str (if (and template (str/starts-with? template "file://"))
                       (let [path (subs template 7)] ; Remove "file://" prefix
                         (slurp path))
                       (or template ""))
        combined-context (str "## Codebase Files\n\n" codebase-files "\n\n"
                              "## Git History\n\n" git-history)

        ;; Replace all placeholders
        rendered (-> template-str
                     (str/replace "{{context}}" combined-context)
                     (str/replace "{{codebase-files}}" (or codebase-files ""))
                     (str/replace "{{git-history}}" (or git-history ""))
                     (str/replace "{{project-dir}}" (or project-dir (:project-dir ctx) ".")))]

    {:status :ok
     :text rendered}))
