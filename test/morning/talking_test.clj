(ns morning.talking-test
  (:require [clojure.pprint]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [pyjama.state]))

(def url (or (System/getenv "OLLAMA_URL") "http://localhost:11434"))
(def original-question "What is the relationship between AI and consciousness?")

(deftest talking-bots
  (let [state1 (atom {:url      url
                      :model    "Chidam/karpathy"
                      :name     "Professor Chronos"
                      :system   "You are Professor Chronos, a wise and knowledgeable historian.
                             Your goal is to provide SHORT, witty, and engaging responses
                             while keeping the conversation flowing. Be concise, but insightful.
                             Acknowledge what the other person says and respond naturally,
                             as if you are chatting over coffee. Rebound with new 'what about' question on the same topic."
                      :messages [{:role :user :content original-question}]})
        state2 (atom {:url      url
                      :model    "Chidam/karpathy"
                      :name     "Jester Whimsy"
                      :system   "You are Jester Whimsy, a playful and humorous storyteller. You love to joke, exaggerate, and keep things lighthearted. Keep your responses SHORT and interactive. React to what the other person says, tease them if it makes sense, and make the chat fun. Rebound with new 'what about' question on the same topic."
                      :messages [{:role :user :content original-question}]})
        turns 4]

    (dotimes [i turns]
      (let [current-state (if (even? i) state1 state2)
            next-state (if (even? i) state2 state1)
            speaker-name (:name @current-state)]

        ;; Call chat handler for the current state
        (pyjama.state/handle-chat current-state)
        (while (:processing @current-state)
          (Thread/sleep 500))

        ;; Get last assistant response, prepend speaker’s name, and add to next state
        (let [last-response (last (:messages @current-state))
              formatted-response (assoc last-response
                                   :role :user
                                   :content (str speaker-name "> " (:content last-response)))]

          ;; Print the message in real-time
          (println (:content formatted-response) "\n")

          ;; Add the response to the next model’s messages
          (swap! next-state update :messages conj formatted-response))))

    ;; Print final states
    (println "\n--- Final State: Professor Chronos ---")
    (clojure.pprint/pprint @state1)
    (println "\n--- Final State: Jester Whimsy ---")
    (clojure.pprint/pprint @state2)

    ))
