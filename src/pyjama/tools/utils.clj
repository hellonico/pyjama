(ns pyjama.tools.utils)

(defn passthrough
  "Pass arguments through unchanged, used for control flow steps."
  [args]
  (assoc args :status :ok))
