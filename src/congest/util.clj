(ns congest.util)

(defn dlog [message f & args]
  (println message)
  (apply f args))
