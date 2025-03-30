(ns congest.jobs
  (:require
   [overtone.at-at :as at]))

(defn- -get-time []
  (.getTime (new java.util.Date)))

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

(defn- -stop! [*jobs job-id kill?]
  (let [{:keys [stop]} (get-in @*jobs [job-id])]
    (println "Stopping job: " job-id)
    (cond
      kill?
      (at/kill stop)

      :else
      (at/stop stop))))

(defn- -deregister! [*jobs job-id]
  (cond
    (some? (get-in @*jobs [job-id]))
    (do (-stop! *jobs job-id true)
        (swap! *jobs dissoc job-id))

    :else
    false))

(defn- -deregister-recurring-job? [metadata]
  (let [kill-after (:kill-after metadata)]
    (cond (some? kill-after)
          (>= (if-some [num-calls (:num-calls metadata)] num-calls 0) kill-after)

          :else
          false)))

(defn- -deregister-job? [metadata]
  (cond (:recurring? metadata)
        (-deregister-recurring-job? metadata)

        :else
        true))

(defn- -wrapper-internal [*jobs handler metadata]
  (handler metadata)
  (println "metadata in wrapper internal: " metadata)
  (when (:recurring? metadata)
    (swap! *jobs assoc
           (:id metadata)
           (assoc
            metadata
            :num-calls
            (if-some [num-calls (:num-calls metadata)]
              (inc num-calls)
              1)))))

(defn- -wrapper [*jobs handler job-id]
  (fn []
    (let [metadata (get-in @*jobs [job-id])]
      (-wrapper-internal *jobs handler metadata)
      (when (-deregister-job? metadata)
        (-deregister! *jobs job-id)))))

(defn- -register! [*jobs pool opts]
  (let [id (:id opts)]
    (when-not (some? (get-in @*jobs [id]))
      (->> (-start-job
            pool
            (-wrapper *jobs (:handler opts) id)
            opts)
           (assoc opts :created-at (-get-time) :stop)
           (swap! *jobs assoc id)))))

(defn- -read-jobs [*jobs])

(defn dlog [message f & args]
  (println "log something beforehand:" message)
  (apply f args)
  (println "lo something after"))


;; if recurring then kill-after is a number
;; if not recurring then kill-after is a boolean
(defn- -start-jobs-pool [jobs-pool *jobs list-of-jobs-metadata]
  (println list-of-jobs-metadata)
  (run! (fn [job-metadata]
          (-register! *jobs jobs-pool job-metadata))
        (or list-of-jobs-metadata []))
  jobs-pool)

(defn- -create-and-start-jobs-pool [*jobs list-of-jobs-metadata]
  (->  (at/mk-pool)
       (-start-jobs-pool *jobs list-of-jobs-metadata)))

(defprotocol Jobs
  (register! [this opts])
  (deregister! [this job-id])
  (stop! [this job-id kill?])
  (read-jobs [this])
  (kill [this]))

(defn create-jobs [initial-data]
  (let [*jobs (atom {})
        job-pool (-create-and-start-jobs-pool
                  *jobs
                  initial-data)]
    (reify Jobs
      (register! [_ opts]
        (-register! *jobs job-pool opts))
      (deregister! [_ job-id]
        (-deregister! *jobs job-id))
      (stop! [_ job-id kill?]
        (-stop! *jobs job-id kill?))
      (kill [_]
        (at/stop-and-reset-pool! job-pool :strategy :kill)))))

(comment
  (def js (create-jobs
           [{:initial-delay 1000,
             :auto-start true,
             :stop-after-fail false,
             :id "test",
             :kill-after 2000,
             :num-calls 0,
             :interval 1000,
             :recurring? true,
             :created-at nil,
             :handler (fn [metadata] (println "PING")),
             :sleep false}]))
  (stop! js "test" false)
  (kill js)

  (def initial-data (load-file "./resources/test/jobs/initial-data/initial-data-0.clj"))
  (def js (create-jobs initial-data))
  (stop! js "test" false)
  (kill js))