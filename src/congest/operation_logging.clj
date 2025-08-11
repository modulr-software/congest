(ns congest.operation-logging)

(defprotocol Logger
  (log-info! [this opts])
  (log-error! [this opts]))
