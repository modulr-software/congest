(ns congest.jobs
  (:require
   [overtone.at-at :as at]))

(defn- -wrapper [*the-atom handler job-id]
  (println "in -wrapper")
  (fn []
    (let [metadata (get-in @*the-atom [job-id])]
      (println "value? " metadata)
      (handler metadata)
      (println "stop function? " (some? (:stop metadata))))))

(defn- -start-job [pool handler opts]
  (cond
    ;; test with initial delay nil
    (:recurring? opts)
    (at/every
     (:interval opts)
     handler
     pool
     :initial-delay
     (:initial-delay opts))

    :else
    (at/after (:interval opts) handler pool)))

(defn- -register! [*jobs pool handler opts]
  (let [id (:id opts)
        stop (-start-job
              pool
              (-wrapper *jobs handler id)
              opts)]
    (->> (assoc opts :stop stop)
         (swap!
          *jobs
          assoc
          id))))

(defn- -deregister! [*jobs job-id])

(defn- -stop! [*jobs job-id kill?]
  (let [{:keys [stop id]} (get-in @*jobs [job-id])]
    (cond
      kill?
      (at/kill stop)

      :else
      (at/stop stop))))

(defn- -read-jobs [ctx])

(defn dlog [message f & args]
  (println "log something beforehand:" message)
  (apply f args)
  (println "lo something after"))


;; if recurring then kill-after is a number
;; if not recurring then kill-after is a boolean
(defn- -start-jobs-pool [jobs-pool *jobs list-of-jobs-metadata]
  (println list-of-jobs-metadata)
  (run! (fn [job-metadata]
          (-register! *jobs jobs-pool (:handler job-metadata) job-metadata))
        (or list-of-jobs-metadata []))
  jobs-pool)

(defn- -create-and-start-jobs-pool [*jobs list-of-jobs-metadata]
  (->  (at/mk-pool)
       (-start-jobs-pool *jobs list-of-jobs-metadata)))

(defprotocol Jobs
  (register! [this handler opts])
  (deregister! [this job-id])
  (stop! [this job-id kill?])
  (read-jobs [this]))

(defn create-jobs [initial-data]
  (let [*jobs (atom {})
        job-pool (-create-and-start-jobs-pool
                  *jobs
                  initial-data)]
    (reify Jobs
      (register! [_ handler opts]
        (-register! *jobs job-pool handler opts))
      (deregister! [_ job-id]
        (-deregister! *jobs job-id))
      (stop! [_ job-id kill?]
        (-stop! *jobs job-id kill?)))))

(comment
  (def js (create-jobs
           [{:interval 5000
             :handler (fn [metadata] (println "PING"))
             :recurring? true
             :kill-after nil
             :stop-after-fail false
             :auto-start true
             :sleep false
             :initial-delay 1000
             :id "test"}]))
  (stop! js "test" false))