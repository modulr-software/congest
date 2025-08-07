(ns congest.jobs
  (:require [overtone.at-at :as at]
            [congest.operation-logging :as logging])
  (:import (java.text SimpleDateFormat)))

(defn- -get-time []
  (.getTime (new java.util.Date)))

(defn get-formatted-time []
  (let [formatter (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss")]
    (.format formatter (-get-time))))

(defn- -start-job
  [pool handler {:keys [logger recurring? interval initial-delay id] :or {logger (logging/->DefaultLogger)} :as _opts}]
  (if
   ;; test with initial delay nil
   recurring?
    (do
      (logging/log-info!
       logger
       (str "Starting recurring job with job-id '" id "' with " interval "ms interval and " initial-delay "ms delay."))
      (at/every
       interval
       handler
       pool
       :initial-delay
       (max initial-delay 100))) ;; set the minimum initial delay to 100

    (do
      (logging/log-info!
       logger
       (str "Starting non-recurring job with job-id '" id "' with " interval "ms delay."))
      (at/after interval handler pool))))

(defn- -create-stop [pool handler {:keys [logger id] :or {logger (logging/->DefaultLogger)} :as opts}]
  (let [stop (-start-job pool handler opts)]
    (fn
      ([]
       (logging/log-info! logger (str "Stopping job-id '" id "'..."))
       (at/stop stop)) ;; Log message before stopping the job

      ([kill?]
       (if kill?
         (do (logging/log-info! logger (str "Killing job-id '" id "'..."))
             (at/kill at/kill stop)) ;; Log message before killing the job
         (do (logging/log-info! logger (str "Stopping job-id '" id "'..."))
             (at/stop stop))))))) ;; Log message before stopping the job

(defn- -stop! [*jobs job-id kill?]
  (let [{:keys [stop]} (get-in @*jobs [job-id])]
    (if
     kill?
      (stop true)

      (stop false))))

(defn- -deregister! [*jobs job-id]
  (when
   (some? (get-in @*jobs [job-id]))
    (-stop! *jobs job-id true)
    (swap! *jobs dissoc job-id)))

(defn- -handle-with-retries
  ([opts job]
   (-handle-with-retries opts job 0))

  ([{:keys [logger id] :or {logger (logging/->DefaultLogger)}} job tries]
   (let [handler (:handler job)
         max-retries (or (:max-retries job) 0)
         _ (logging/log-info! logger (str "Attempting to run job-id '" id "': attempt " (inc tries) " of " (inc max-retries) "..."))
         event (handler job)]
     (if (and (< tries max-retries)
              (= event :fail))
       (do
         (logging/log-error! logger (str "Attempt " (inc tries) " of " (inc max-retries) " running job-id '" id "' failed."))
         (-handle-with-retries job
                               (inc tries)))
       (do
         (logging/log-info! logger (str "Attempt " (inc tries) " of " (inc max-retries) " running job-id '" id "' succeeded."))
         (assoc job :event (or event :success))))))) ;; if event is nil then we default to success

(defmulti -maybe-deregister (fn [job] (:recurring? job)))

(defmethod -maybe-deregister true [{:keys [stop-after-fail?
                                           stop
                                           kill-after] :as job}]
  (cond (= (:event job) :fail)
        (if (and (some? stop-after-fail?) (not stop-after-fail?))
          (assoc job :num-fails (inc (or (:num-fails job) 0)))

          (do
            (stop)
            nil))

        ;; kill-after with a value of 0 should never exist
        (and (some? kill-after) (>= (or (:num-calls job) 0) kill-after))
        (do
          (stop)
          nil)

        :else
        job))

(defmethod -maybe-deregister false []
  nil)

(defn- -increase-calls [job]
  (when (some? job)
    (assoc job :num-calls (inc (or (:num-calls job) 1)))))

(defn- -post-run-cleanup [job]
  (when (some? job)
    (dissoc job :event)))

(defn- -run-job [opts job]
  (->> job
       (-handle-with-retries opts)
       (-maybe-deregister)
       (-increase-calls)
       (-post-run-cleanup)))

(defn- -wrapper [*jobs {:keys [id] :as opts}]
  (fn []
    (->> (get-in @*jobs [id])
         (-run-job opts)
         (swap! *jobs assoc id))))

(defn- -register! [*jobs pool {:keys [id] :as opts}]
  (when-not (some? (get-in @*jobs [id]))
    (->> (-create-stop
          pool
          (-wrapper *jobs opts)
          opts)
         (assoc opts :created-at (-get-time) :stop)
         (swap! *jobs assoc id))))

(defn- -start-jobs-pool [jobs-pool *jobs list-of-jobs-metadata]
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
  (kill [this]))

(defn create-job-service [initial-data]
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
  (get-formatted-time)

  (def initial-data-1 [])
  (def initial-data-2 [{:initial-delay 10
                        :auto-start true
                        :stop-after-fail false,
                        :id "test"
                        :kill-after 1
                        :num-calls nil
                        :interval 1000
                        :recurring? true
                        :created-at nil
                        :handler (fn [metadata] (println "RUN"))
                        :sleep false}])

  (def js (create-job-service initial-data-2))
  (stop! js "test" false))
