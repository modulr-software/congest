(ns congest.jobs
  (:require
   [overtone.at-at :as at]))

(defn- -wrapper [*the-atom handler job-id]
  (println "in -wrapper")
  (fn []
    (let [metadata (get-in @*the-atom [job-id])]
      (println "value? " (some? metadata))
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
  (let [id (:id opts)]
    (swap!
     *jobs
     assoc
     id
     {:stop (-start-job
             pool
             (-wrapper *jobs handler id)
             opts)})))

(defn- -deregister! [*jobs pool job-id])

(defn- -stop! [*jobs job-id kill?]
  (cond
    kill?
    (at/kill (get-in @*jobs [job-id :stop]))

    :else
    (at/stop ())))

(defn- -read-jobs [ctx])

(defn dlog [message f & args]
  (println "log something beforehand:" message)
  (apply f args)
  (println "lo something after"))

(dlog "running action blah blah blah" create-jobs [{}])
*
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
  (stop! [this job-id])
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
        (-deregister! *jobs job-pool job-id))
      (stop! [_ job-id]
        (-stop! *jobs job-pool job-id)))))

;; START UP AND SHUT DOWN HOOKS 
;; Read colleciton of jobs and stop them all
(defn stop-all-jobs [jobs])

;; Read jobs from  and returns a new cron job service instance?
(defn read-jobs [])

;; Takes a cron job service and start all dormant jobs
(defn start-all-jobs [*jobs])

;; JOB REGISTER AND DE-REGISTER SERVICES 
;; Register a job to the cron service and start it
(defn- -register-job! [jobs handler & opts]
  (let [auto-start (or (:auto-start opts) true)]))

;; Deregister and stop a job from the cron service
(defn- -deregister-job [jobs job-id])

;; Stop a job
(defn- -stop-job [jobs job-id])

;; for a given job the meta data we need is:
;; its id
;; should it auto start (only necessary to know when the job is registered?)
;; should it be running or not
;; is it recurring?
;; what is its interval
;; should it execute its handler at start or a certain time after start
(defn handler [request]
  (println request))


(comment
  (let [*the-atom (atom {}) job-id "test-id" job-pool (at/mk-pool)]
    (swap!
     *the-atom
     assoc
     job-id
     {:stop (at/after 1 (wrapper *the-atom handler job-id) job-pool
                      :name "My Name")})
    (Thread/sleep 1000)
    (at/shutdown-pool!
     @(:pool-atom job-pool)
     :kill))
  (let [job-pool (at/mk-pool)]
    (at/every 1 (fn [request] (println "thing")) job-pool)
    (at/shutdown-pool! @(:pool-atom job-pool) :kill)))

(comment
  (create-jobs
   [{:interval 5000
     :handler (fn [metadata] (println "PING"))
     :recurring? true
     :kill-after nil
     :stop-after-fail false
     :auto-start true
     :sleep false
     :initial-delay 1000
     :id "test"}]))