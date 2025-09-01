(ns pyjama.doc.spinner)

;; ---------- spinner helpers ----------

(def ^:private spinner-frames [\| \/ \- \\])

(defn tty? []
  ;; Only spin if we have a real console
  (some? (System/console)))

(defn ^:private now-nanos [] (System/nanoTime))

(defn ^:private fmt-secs [nanos]
  (format "%.2fs" (double (/ nanos 1.0e9))))

(defn with-spinner
  "Runs (thunk) while rendering a spinner with elapsed time and a label.
   Spinner shows only when (tty?) and spinner? is truthy.
   Always stops/clears even on exceptions."
  [spinner? label thunk]
  (if (and spinner? (tty?))
    (let [running (atom true)
          start   (now-nanos)
          t       (doto (Thread.
                          (fn []
                            (loop [i 0]
                              (when @running
                                (let [frame (nth spinner-frames (mod i (count spinner-frames)))
                                      elapsed (fmt-secs (- (now-nanos) start))
                                      msg (str "\r" frame " " label " [" elapsed "]")]
                                  (print msg)
                                  (flush)
                                  (Thread/sleep 120)
                                  (recur (inc i)))))))
                    (.setDaemon true)
                    (.start))]
      (try
        (thunk)
        (finally
          (reset! running false)
          ;; Clear the spinner line and print a done tick with final timing.
          (let [elapsed (fmt-secs (- (now-nanos) start))]
            (print (str "\r" (apply str (repeat 80 \space)) "\r")) ; clear line
            (println (str "✔ " label " (" elapsed ")"))
            (flush)))))
    ;; no spinner path
    (thunk)))

