(ns congest.db
  (:require [next.jdbc :as jdbc]
            [congest.config :as config]))

(def ^:private db-config {:dbtype "sqlite"
                          :dbname (:dbname config/env)})

(defprotocol Database)

(defn create-db-connection []
  (let [ds (jdbc/get-datasource db-config)]
    (reify Database)))