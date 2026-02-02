(ns pyjama.agent.visualize
  "Mermaid diagram generation for Pyjama agents"
  (:require [clojure.string :as str]))

(defn visualize-mermaid
  "Generate a Mermaid flowchart diagram for an agent graph.
   Returns the mermaid diagram as a string."
  [agent-id spec]
  (let [start (:start spec)
        steps (:steps spec)
        sb (StringBuilder.)]

    ;; Header
    (.append sb "```mermaid\n")
    (.append sb "flowchart TD\n")
    (.append sb (str "    Start([" agent-id "]) --> " (name start) "\n\n"))

    ;; Helper functions
    (letfn [(node-name [k] (str/replace (name k) #"[^a-zA-Z0-9_]" "_"))
            (node-label [k] (str/replace (name k) #"-" " "))

            (format-path [path]
              "Format a path like [:obs :items] to 'obs items'"
              (if (vector? path)
                (str/join " " (map name (filter keyword? path)))
                (str path)))

            (format-condition [condition]
              "Format a condition to be human-readable"
              (cond
                (nil? condition) "else"
                (vector? condition)
                (let [[op arg1 arg2] condition]
                  (case op
                    :nonempty (str "nonempty " (format-path arg1))
                    :> (str (format-path arg1) " > " arg2)
                    :< (str (format-path arg1) " < " arg2)
                    := (str (format-path arg1) " = " (pr-str arg2))
                    (pr-str condition)))  ; default case for unknown operators
                :else (pr-str condition)))

            (node-def [step-id step]
              (let [nname (node-name step-id)
                    label (node-label step-id)]
                (cond
                  (and (:loop-over step) (:loop-body step))
                  (str nname "{{" label "}}")

                  (:tool step)
                  (str nname "[" label "<br/>Tool: " (name (:tool step)) "]")

                  (:parallel step)
                  (str nname "[[" label "<br/>Parallel]]")

                  (:routes step)
                  (str nname "{" label "}")

                  :else
                  (str nname "(" label "<br/>LLM)"))))

            (node-style [step-id step]
              (cond
                (and (:loop-over step) (:loop-body step))
                (str "    style " (node-name step-id) " fill:#f9a825,stroke:#f57f17\n")

                (:tool step)
                (str "    style " (node-name step-id) " fill:#42a5f5,stroke:#1976d2\n")

                (:parallel step)
                (str "    style " (node-name step-id) " fill:#ab47bc,stroke:#7b1fa2\n")

                (:routes step)
                (str "    style " (node-name step-id) " fill:#ffa726,stroke:#f57c00\n")

                :else
                (str "    style " (node-name step-id) " fill:#66bb6a,stroke:#388e3c\n")))]

      ;; Process all steps
      (doseq [[step-id step] steps]
        (let [nname (node-name step-id)]

          ;; Define the node
          (.append sb (str "    " (node-def step-id step) "\n"))

          ;; Handle different step types
          (cond
            ;; Loop step
            (and (:loop-over step) (:loop-body step))
            (do
              (.append sb (str "    " nname " -->|loop over<br/>"
                               (format-path (:loop-over step)) "| "
                               (node-name (:loop-body step)) "\n"))
              (.append sb (str "    " (node-name (:loop-body step))
                               " -.->|each item| " nname "\n"))
              (when (:next step)
                (.append sb (str "    " nname " -->|after loop| "
                                 (node-name (:next step)) "\n"))))

            ;; Parallel step
            (:parallel step)
            (do
              (doseq [branch (:parallel step)]
                (.append sb (str "    " nname " -->|parallel| "
                                 (node-name branch) "\n")))
              (when (:next step)
                (.append sb (str "    " nname " -->|join| "
                                 (node-name (:next step)) "\n"))))

            ;; Routing step
            (:routes step)
            (do
              (doseq [route (:routes step)]
                (let [next-step (or (:next route) (:else route))]
                  (when next-step
                    (let [condition (if (:else route) "else" (format-condition (:when route)))]
                      (.append sb (str "    " nname " -->|" condition "| "
                                       (node-name next-step) "\n"))))))
              (when (and (:next step)
                         (not (some #(or (:next %) (:else %)) (:routes step))))
                (.append sb (str "    " nname " --> "
                                 (node-name (:next step)) "\n"))))

            ;; Simple next step
            (:next step)
            (.append sb (str "    " nname " --> "
                             (node-name (:next step)) "\n"))

            ;; Terminal step (goes to done)
            :else
            (.append sb (str "    " nname " --> done\n")))))

      ;; Add done node
      (.append sb "    done([Done])\n\n")

      ;; Add styles
      (doseq [[step-id step] steps]
        (.append sb (node-style step-id step)))

      ;; Style Start and done
      (.append sb "    style Start fill:#4caf50,stroke:#2e7d32\n")
      (.append sb "    style done fill:#ef5350,stroke:#c62828\n")

      (.append sb "```\n"))

    (.toString sb)))

(defn visualize-mermaid-file
  "Generate a Mermaid diagram and save it to a file.
   Returns the file path."
  [agent-id spec output-path]
  (let [diagram (visualize-mermaid agent-id spec)]
    (spit output-path diagram)
    output-path))
