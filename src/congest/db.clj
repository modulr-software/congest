(ns congest.db
  (:require [next.jdbc :as jdbc]
            [congest.config :as config]))

(def ^:private db-config {:dbtype "sqlite"
                          :dbname (:dbname config/env)})
(defn- create-db []
  db-config)

(defn- create-data-source [db]
  (jdbc/get-datasource db))

(create-data-source (create-db))