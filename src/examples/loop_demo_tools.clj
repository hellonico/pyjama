(ns examples.loop_demo_tools
  "Tool implementations for loop-demo-agent example")

(defn create-sample-items
  "Create sample items for batch processing"
  [{:keys [count]}]
  {:items (vec (for [i (range count)]
                 {:id (inc i)
                  :name (str "Item-" (inc i))
                  :priority (rand-nth ["high" "medium" "low"])
                  :status "pending"}))})

(defn process-item
  "Process a single item"
  [{:keys [item]}]
  (println (str "  Processing: " (:name item) " (priority: " (:priority item) ")"))
  (Thread/sleep 100)  ; Simulate work
  {:status "completed"
   :item-id (:id item)
   :result (str "Processed " (:name item))})

(defn fetch-data
  "Fetch sample issue data"
  [_]
  {:items [{:id 1 :title "Bug in login" :description "Users can't log in"}
           {:id 2 :title "Feature request" :description "Add dark mode"}
           {:id 3 :title "Performance issue" :description "Slow page load"}]})
