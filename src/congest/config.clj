(ns congest.config
  (:require [aero.core :as aero]))

(def env (aero/read-config "config.edn"))