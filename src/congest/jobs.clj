(ns congest.jobs
  (:require
   [overtone.at-at :as at]
   [congest.util :as util]))

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

(defn- -stop-job-handler [pool handler opts]
  (let [stop (-start-job pool handler opts)]
    (fn
      ([]
       (util/dlog "Stopping job" at/stop stop)) ;; Log message before stopping the job

      ([kill?]
       (if kill?
         (util/dlog "Killing job" at/kill stop) ;; Log message before killing the job
         (util/dlog "Stopping job" at/stop stop)))))) ;; Log message before stopping the job

(defn- -stop! [*jobs job-id kill?]
  (let [{:keys [stop]} (get-in @*jobs [job-id])]
    (cond
      kill?
      (stop true)

      :else
      (stop false))))

(defn- -deregister! [*jobs job-id]
  (cond
    (some? (get-in @*jobs [job-id]))
    (do (-stop! *jobs job-id true)
        (swap! *jobs dissoc job-id))

    :else
    false))

(defn- -handle-with-retries
  ([job]
   (-handle-with-retries job 0))

  ([job tries]
   (let [handler (:handler job)
         max-retries (:max-retries job)
         event (handler job)]
     (if (and (< tries (or max-retries 0))
              (= event :fail))
       (-handle-with-retries job
                             (inc tries))

       (assoc job :event (or event :success)))))) ;; if event is nil then we default to success

(defmulti -maybe-deregister (fn [job] (:recurring? job)))

(defmethod -maybe-deregister true [{:keys [stop-after-fail?
                                           stop
                                           kill-after] :as job}]
  (cond (= (:event job) :fail)
        (if (and (some? stop-after-fail?) (not stop-after-fail?))
          (assoc job :num-fails (inc (or (:num-fails job) 1)))

          (do
            (stop)
            nil))

        (and (some? kill-after) (>= (or (:num-calls job) 1) kill-after))
        (do
          (println (:num-calls job))
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

(defn- -run-job [job]
  (->> job
       (-handle-with-retries)
       (-maybe-deregister)
       (-increase-calls)
       (-post-run-cleanup)))

(defn- -wrapper [*jobs job-id]
  (fn []
    (->> (get-in @*jobs [job-id])
         (-run-job)
         (swap! *jobs assoc job-id))))

(defn- -register! [*jobs pool opts]
  (let [id (:id opts)]
    (when-not (some? (get-in @*jobs [id]))
      (->> (-stop-job-handler
            pool
            (-wrapper *jobs id)
            opts)
           (assoc opts :created-at (-get-time) :stop)
           (swap! *jobs assoc id))))
  (println "Job has been registered"))

;; if recurring then kill-after is a number
;; if not recurring then kill-after is a boolean
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

  (def js (create-jobs initial-data-2))
  (stop! js "test" false)
  (kill js))