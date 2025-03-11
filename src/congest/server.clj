(ns congest.server
  (:require
   [org.httpkit.server :as http]
   [congest.config :as config]))

(defonce ^:private *server (atom nil))

(defn- app [req]
  {:status 200
   :body "Welcome to congest"
   :headers {"Content-Type" "application/json"}})

(defn running? []
  (not (nil? @*server)))

(defn start-server! []
  (when (not (running?))
    (->>
     (http/run-server app {:port (:port config/env)})
     (reset! *server))))

(defn stop-server! []
  (when (running?)
    (@*server)
    (reset! *server nil)))

(defn restart-server! [] (stop-server!)
  (start-server!))