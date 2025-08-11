(ns congest.jobs
  (:require [overtone.at-at :as at])
  (:import (java.text SimpleDateFormat)))

(defn- -get-time []
  (.getTime (new java.util.Date)))

(defn get-formatted-time []
  (let [formatter (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss")]
    (.format formatter (-get-time))))

(defn- -start-job
  [pool handler {:keys [logger recurring? interval initial-delay] :as opts}]
  (if
   ;; test with initial delay nil
   recurring?
    (do
      (when (some? logger)
        (logger (merge opts {:log-time (get-formatted-time)
                             :action "start"})))
      (at/every
       interval
       handler
       pool
       :initial-delay
       (max initial-delay 100))) ;; set the minimum initial delay to 100

    (do
      (when (some? logger)
        (logger (merge opts {:log-time (get-formatted-time)
                             :action "start"})))
      (at/after interval handler pool))))

(defn- -create-stop [pool handler {:keys [logger] :as opts}]
  (let [extended-opts (merge opts {:log-time (get-formatted-time)})
        stop (-start-job pool handler opts)]
    (fn
      ([]
       (when (some? logger)
         (logger (merge extended-opts {:action "stop"})))
       (at/stop stop)) ;; Log message before stopping the job

      ([kill?]
       (if kill?
         (do (when (some? logger)
               (logger (merge extended-opts {:action "kill"})))
             (at/kill at/kill stop)) ;; Log message before killing the job
         (do (when (some? logger)
               (logger (merge extended-opts {:action "stop"})))
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

  ([{:keys [logger] :as opts} job tries]
   (let [handler (:handler job)
         max-retries (or (:max-retries job) 0)
         extended-opts (merge opts {:log-time (get-formatted-time)
                                    :action "run"
                                    :tries tries
                                    :max-retries max-retries})
         _ (logger extended-opts)
         event (handler job)]
     (if (and (< tries max-retries)
              (> max-retries 0)
              (= event :fail))
       (do
         (logger (merge extended-opts {:event :fail}))
         (-handle-with-retries opts job
                               (inc tries)))
       (if (= event :fail)
         (do
           (logger (merge extended-opts {:event :fail}))
           (assoc job :event :fail))
         (do
           (logger (merge extended-opts {:event :success}))
           (assoc job :event (or event :success)))))))) ;; if event is nil then we default to success

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

(defn- -register! [*jobs pool {:keys [logger id] :as opts}]
  (when-not (some? (get-in @*jobs [id]))
    (when (some? logger)
      (logger (merge opts {:log-time (get-formatted-time)
                           :action "register"})))
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
  (kill! [this]))

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
      (kill! [_]
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
                        :handler (fn [metadata] (println "RUN") :fail)
                        :logger (fn [opts] (println opts))
                        :sleep false}])

  (def js (create-job-service initial-data-2))
  (stop! js "test" false))
