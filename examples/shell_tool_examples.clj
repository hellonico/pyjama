(ns shell-tool-examples
  "Examples demonstrating environment variable expansion and glob patterns in shell tool"
  (:require [pyjama.tools.shell :as shell]))

(comment
  ;; ============================================================
  ;; Environment Variable Expansion Examples
  ;; ============================================================

  ;; Basic $VAR expansion
  (shell/execute-command {:command "echo $HOME"})
  ;; => Expands to /Users/username or /home/username

  ;; ${VAR} syntax
  (shell/execute-command {:command "echo ${USER}_backup"})
  ;; => Expands to username_backup

  ;; Multiple env vars
  (shell/execute-command {:command "echo $USER works at $HOME"})
  ;; => Expands both variables

  ;; In working directory paths
  (shell/execute-command
   {:command "ls -la"
    :dir "$HOME/projects"})
  ;; => $HOME is expanded in the :dir argument

  ;; Disable env expansion when needed
  (shell/execute-command
   {:command "echo '$HOME is your home'"
    :expand-env? false})
  ;; => Literally prints "$HOME is your home"

  ;; ============================================================
  ;; Glob Pattern Expansion Examples
  ;; ============================================================

  ;; List all Clojure files
  (shell/execute-command {:command ["ls" "*.clj"]})
  ;; => Expands to file1.clj file2.clj file3.clj

  ;; List files in subdirectory
  (shell/execute-command {:command ["ls" "src/*.clj"]})
  ;; => Lists all .clj files in src/

  ;; Single character match
  (shell/execute-command {:command ["ls" "file?.txt"]})
  ;; => Matches file1.txt file2.txt fileA.txt

  ;; Character set match
  (shell/execute-command {:command ["ls" "file[123].txt"]})
  ;; => Matches only file1.txt file2.txt file3.txt

  ;; Disable glob expansion
  (shell/execute-command
   {:command ["echo" "*.txt"]
    :expand-glob? false})
  ;; => Literally prints "*.txt"

  ;; ============================================================
  ;; Combined Environment Variables + Globs
  ;; ============================================================

  ;; List PDFs in home directory
  (shell/execute-command {:command "ls $HOME/*.pdf"})
  ;; => Expands $HOME, then expands *.pdf

  ;; Find all markdown files in user's Documents
  (shell/execute-command {:command ["find" "$HOME/Documents" "-name" "*.md"]})
  ;; => $HOME expanded, *.md passed to find

  ;; Backup all config files
  (shell/execute-command
   {:command "tar -czf $HOME/backup.tar.gz $HOME/.config/*.conf"})
  ;; => Both $HOME instances and *.conf expanded

  ;; ============================================================
  ;; Practical Use Cases
  ;; ============================================================

  ;; 1. Count lines in all source files
  (shell/execute-command {:command "wc -l src/**/*.clj"})

  ;; 2. Search for pattern in project files
  (shell/execute-command {:command ["grep" "-r" "defn" "src/*.clj"]})

  ;; 3. Clean up temp files
  (shell/execute-command {:command "rm /tmp/*.tmp"})

  ;; 4. Create backup with timestamp
  (shell/execute-command
   {:command "cp $HOME/important.txt $HOME/important-$(date +%Y%m%d).txt"})

  ;; 5. Check disk usage of home directory
  (shell/execute-command {:command "du -sh $HOME/*"})

  ;; 6. List recent log files
  (shell/execute-command {:command "ls -lt /var/log/*.log | head -5"})

  ;; ============================================================
  ;; Scripts with Expansion
  ;; ============================================================

  ;; Multi-line script with env vars and globs
  (shell/run-script
   {:script "#!/bin/bash
             echo 'User: $USER'
             echo 'Home: $HOME'
             echo 'Clojure files:'
             ls -1 *.clj 2>/dev/null || echo 'No .clj files found'
             echo 'Done!'"})

  ;; Python script with env var
  (shell/run-script
   {:script "import os
             print(f'User: {os.getenv(\"USER\")}')"
    :shell "/usr/bin/python3"})

  ;; ============================================================
  ;; Controlling Expansion Behavior
  ;; ============================================================

  ;; Both expansions enabled (default)
  (shell/execute-command
   {:command "echo $HOME/*.txt"
    :expand-env? true
    :expand-glob? true})
  ;; => Both $HOME and *.txt expanded

  ;; Only env vars expanded
  (shell/execute-command
   {:command "echo $HOME/*.txt"
    :expand-env? true
    :expand-glob? false})
  ;; => Only $HOME expanded, *.txt literal

  ;; Only globs expanded
  (shell/execute-command
   {:command "echo $HOME/*.txt"
    :expand-env? false
    :expand-glob? true})
  ;; => Only *.txt expanded, $HOME literal

  ;; No expansion
  (shell/execute-command
   {:command "echo $HOME/*.txt"
    :expand-env? false
    :expand-glob? false})
  ;; => Both literal: "$HOME/*.txt"

  ;; ============================================================
  ;; Error Handling
  ;; ============================================================

  ;; Handle missing env var gracefully
  (let [result (shell/execute-command {:command "echo $NONEXISTENT_VAR"})]
    (if (= :ok (:status result))
      (println "Output:" (:out result))
      (println "Error:" (:message result))))

  ;; Handle no glob matches
  (let [result (shell/execute-command {:command ["ls" "*.xyz123"]})]
    (if (= :ok (:status result))
      (println "Files:" (:out result))
      (println "No matches or error"))))
