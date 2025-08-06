(ns congest.logging
  (:require [congest.jobs :refer [get-formatted-time]]))

(defprotocol Logger
  (log! [this level message])
  (log-info! [this message])
  (log-error! [this message]))

(defrecord DefaultLogger []
  Logger
  (log! [_ level message]
    (println (str "[" (get-formatted-time) "] [" level "] " message)))
  (log-info! [_ message]
    (println (str "[" (get-formatted-time) "] [INFO] " message)))
  (log-error! [_ message]
    (println (str "[" (get-formatted-time) "] [ERROR] " message))))
