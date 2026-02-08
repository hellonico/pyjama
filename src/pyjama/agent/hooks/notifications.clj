(ns pyjama.agent.hooks.notifications
  "Notification hooks for agent tool execution.
  
  Provides hooks for sending notifications on various events:
  - Tool completion
  - Errors
  - Long-running operations
  - Custom conditions"
  (:require [clojure.string :as str]))

;; Notification handlers registry
(defonce ^:private handlers (atom {}))

(defn register-handler!
  "Register a notification handler.
  
  Args:
    handler-id - Keyword identifying the handler
    handler-fn - Function that receives notification data and sends it
                 Should accept: {:title :message :level :data}
  
  Example:
    (register-handler! :slack
      (fn [{:keys [title message level]}]
        (send-to-slack {:text (str title \": \" message)})))"
  [handler-id handler-fn]
  (swap! handlers assoc handler-id handler-fn)
  (println (str "✓ Registered notification handler: " handler-id)))

(defn unregister-handler!
  "Unregister a notification handler."
  [handler-id]
  (swap! handlers dissoc handler-id)
  (println (str "✓ Unregistered notification handler: " handler-id)))

(defn- send-notification!
  "Send a notification to all registered handlers."
  [{:keys [title message level data] :as notification}]
  (doseq [[handler-id handler-fn] @handlers]
    (try
      (handler-fn notification)
      (catch Exception e
        (binding [*out* *err*]
          (println (str "⚠️  Notification handler " handler-id " failed: " (.getMessage e))))))))

(defn notify-on-error
  "Hook that sends notifications when tools fail."
  [{:keys [tool-name result ctx]}]
  (when (= :error (:status result))
    (send-notification!
     {:title "Tool Execution Failed"
      :message (str "Tool " tool-name " failed in agent " (:id ctx))
      :level :error
      :data {:tool tool-name
             :agent (:id ctx)
             :error (:error result)}})))

(defn notify-on-completion
  "Hook that sends notifications when tools complete successfully."
  [{:keys [tool-name result ctx]}]
  (when (= :ok (:status result))
    (send-notification!
     {:title "Tool Execution Complete"
      :message (str "Tool " tool-name " completed in agent " (:id ctx))
      :level :info
      :data {:tool tool-name
             :agent (:id ctx)
             :result result}})))

(defn notify-on-file-written
  "Hook specifically for file write notifications."
  [{:keys [tool-name result ctx]}]
  (when (and (= tool-name :write-file)
             (= :ok (:status result)))
    (send-notification!
     {:title "File Written"
      :message (str "File written: " (:file result))
      :level :info
      :data {:file (:file result)
             :agent (:id ctx)
             :bytes (:bytes result)}})))

;; Built-in notification handlers

(defn console-handler
  "Simple console notification handler."
  [{:keys [title message level]}]
  (let [icon (case level
               :error "❌"
               :warn "⚠️"
               :info "ℹ️"
               :success "✅"
               "📢")]
    (println (str icon " " title ": " message))))

(defn file-handler
  "File-based notification handler."
  [file-path]
  (fn [{:keys [title message level data]}]
    (let [timestamp (str (java.time.Instant/now))
          entry (str timestamp " [" (str/upper-case (name level)) "] "
                     title ": " message "\n")]
      (spit file-path entry :append true))))

(defn webhook-handler
  "Generic webhook notification handler."
  [webhook-url]
  (fn [{:keys [title message level data]}]
    ;; This is a placeholder - in real implementation, would use HTTP client
    (println (str "📡 Would send to webhook: " webhook-url))
    (println (str "   Title: " title))
    (println (str "   Message: " message))
    (println (str "   Level: " level))))

(defn register-notification-hooks!
  "Register notification hooks.
  
  Options:
    :on-error      - Notify on errors (default: true)
    :on-completion - Notify on successful completion (default: false)
    :on-file-write - Notify on file writes (default: false)
    :tools         - Vector of tools to monitor (default: all)"
  [& {:keys [on-error on-completion on-file-write tools]
      :or {on-error true
           on-completion false
           on-file-write false}}]
  (require '[pyjama.agent.hooks :as hooks])
  (let [register! (resolve 'pyjama.agent.hooks/register-hook!)
        tool-list (or tools [:write-file :read-files :list-directory
                             :cat-files :discover-codebase])]

    (when on-error
      (doseq [tool tool-list]
        (register! tool notify-on-error))
      (println "✓ Registered error notification hooks"))

    (when on-completion
      (doseq [tool tool-list]
        (register! tool notify-on-completion))
      (println "✓ Registered completion notification hooks"))

    (when on-file-write
      (register! :write-file notify-on-file-written)
      (println "✓ Registered file write notification hook"))))

(defn unregister-notification-hooks!
  "Unregister all notification hooks."
  []
  (require '[pyjama.agent.hooks :as hooks])
  (let [unregister! (resolve 'pyjama.agent.hooks/unregister-hook!)
        tools [:write-file :read-files :list-directory :cat-files :discover-codebase]]
    (doseq [tool tools]
      (unregister! tool notify-on-error)
      (unregister! tool notify-on-completion))
    (unregister! :write-file notify-on-file-written)
    (println "✓ Unregistered notification hooks")))

(comment
  ;; Example usage

  ;; Register console handler
  (register-handler! :console console-handler)

  ;; Register file handler
  (register-handler! :file (file-handler "/tmp/pyjama-notifications.log"))

  ;; Register webhook handler
  (register-handler! :webhook (webhook-handler "https://hooks.slack.com/..."))

  ;; Enable notifications
  (register-notification-hooks! :on-error true
                                :on-completion true
                                :on-file-write true)

  ;; Test notification
  (send-notification! {:title "Test"
                       :message "This is a test"
                       :level :info
                       :data {}})

  ;; Unregister
  (unregister-notification-hooks!)
  (unregister-handler! :console))
