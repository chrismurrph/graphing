(ns graphing.passing-time

  (:require
    #?@(:clj  [[graphing.interop :as i]
               [graphing.clj-interop :as ci]]
        :cljs [[graphing.interop :as i]
               [graphing.cljs-interop :as ci]
               [clojure.string :as str]
               [cljs.core.async :as async :refer [<! >! chan close! put! timeout]]]))

  #?(:clj (:use [clojure.core.async :only [chan go <! >! go-loop close! thread timeout]] :reload))

  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

  )

(defn format-time
  [time-map]
  (let [{:keys [year month day-of-month hour minute second]} time-map
        res (str month " " day-of-month ", " year " " hour ":" minute ":" second)]
    res))

(defn abs [val]
  (if (neg? val)
    (* -1 val)
    val))

(def abst-time (ci/->CljsTime))

(def last-day-of-months {"Jan" 31 "Feb" 28 "Mar" 31 "Apr" 30 "May" 31 "Jun" 30 "Jul" 31 "Aug" 31 "Sep" 30 "Oct" 31 "Nov" 30 "Dec" 31})

(defn map->host-time [map-time]
  (let [{:keys [year month day-of-month hour minute second]} map-time
        res (i/host-time abst-time year (i/month-as-number abst-time month) day-of-month hour minute second)
        ]
    res))

;;
;; This function returns the difference in milliseconds. Tiny drifts can be adjusted for. Anything else is
;; either a variance or an anomolie.
;; If the return value is +ive then current time has raced ahead, which by convention is what we are intending to
;; happen by making the async wait time exactly five seconds.
;; js-time can't ever be slowed down, but it seems evaluating instructions outside the core-async 5000 wait
;; certainly can be. In fact it won't be so much evaluating the instructions as other things going on in the
;; machine. We can probably (eventually) cater for large discrepancies, even > 5 seconds.
;;
;; new Date(year, month, day, hours, minutes, seconds, milliseconds)
;;
(defn- host-ahead-of-map-by [expected-map js-time]
  (let [expected-time (map->host-time expected-map)
        ;_ (log "expected-time: " expected-time)
        diff (- (.getTime js-time) (.getTime expected-time))
        ;_ (log "Amount that current: " current-time " is more than expected: " expected-time ", is: " diff)
        ;_ (assert (>= (u/abs diff) 500) (str "diff not >= 500, but: " diff))
        ]
    diff))

;;
;; Returns nil if we are at the end of the year, so caller knows to increment the year as well as
;; setting the month to "Jan"
;;
(defn- get-next-month [in-month]
  (if (= in-month "Dec")
    nil
    (nth i/months (inc (i/month-as-number abst-time in-month)))))

(defn- in-n-seconds
  "Following standard conventions, what do we expect the time to be in n seconds from the given time"
  [n in-time-map]
  (assert (< n 10) (str "Only supposed to be used for small numbers, was trying " n))
  (let [{:keys [year month day-of-month hour minute second]} in-time-map
        more-at-the-top (fn [in-seconds] (- (+ in-seconds n) 60))]
    (if (< second (- 60 n))
      (merge in-time-map {:second (+ n second)})
      (if (< minute 59)
        (merge in-time-map {:minute (inc minute) :second (more-at-the-top second)})
        (if (< hour 23)
          (merge in-time-map {:hour (inc hour) :minute 0 :second (more-at-the-top second)})
          (let [max-day (month last-day-of-months)]
            (if (< day-of-month max-day)
              (merge in-time-map {:day-of-month (inc day-of-month) :hour 0 :minute 0 :second (more-at-the-top second)})
              (let [next-month (get-next-month month)]
                (if (nil? next-month)
                  {:year (inc year) :month "Jan" :day-of-month 1 :hour 0 :minute 0 :second (more-at-the-top second)}
                  (merge in-time-map {:month next-month :day-of-month 1 :hour 0 :minute 0 :second (more-at-the-top second)}))))))))))

(defn host->derived-time
  [host-time]
  ;(log "IN:" host-time)
  (let [millis (i/millis-component-of-host-time abst-time host-time)
        {:keys [month day-of-month year hour min sec]} (i/format-from-time abst-time host-time)
        seconds (i/parse-to-int abst-time sec)
        b4-rounding {:year (i/parse-to-int abst-time year) :month month :day-of-month (i/parse-to-int abst-time day-of-month) :hour (i/parse-to-int abst-time hour) :minute (i/parse-to-int abst-time min) :second seconds}
        more-than-half-way-to-next (>= millis 500)]
    ;(log month " " day-of-month " " year " " hour " " min " " sec)
    (if more-than-half-way-to-next
      (in-n-seconds 1 b4-rounding)
      b4-rounding)
    ))

;; If we just generate browser session data then time-zero being when the browser app starts is fine
(def time-zero (atom nil))

(add-watch time-zero :watcher
           (fn [key atom old-state new-state]
             (i/log abst-time (str "time-zero set to: " new-state))))

(defn host-add-seconds []
  ;;
  ;; Thinking about stopping before-variances being calculated again and again...
  ;; (only when the atom has been set can we create this function)
  ;;
  (let [before-variances (map->host-time @time-zero)]
    (fn [seconds]
      (let [given-millis (.getTime before-variances)
            _ (i/log abst-time (str "getTime returns: " given-millis))
            augmented-millis (+ (* seconds 1000) given-millis)
            res (i/host-time abst-time augmented-millis)]
        res))))

;;
;; Record variances in order as they happen. This, time-zero and anomolies will all be durable i.e. be kept
;; in the database. key is the expected time, which is a map, and val is the variance at that time.
;; Note as a general point that expected time should never be shown to a user because it will usually be
;; wrong.
;;
(def variances (atom []))

;; (+ passing-time (reduce + (vals variances)))
(defn- sum-variances-up-to [passing-time]
  0)

;;
;; Return derived-time from passing-time.
;; Derived time might be a map but its definition is from applying all the variances and anomolies to
;; come up with the same as what the machine holds.
;;
(defn passing->derived-time [passing-time]
  (let [variance-additions (sum-variances-up-to passing-time)
        all-additions (+ passing-time variance-additions)
        js-res ((host-add-seconds) all-additions)
        res (host->derived-time js-res)]
    res))

(defn derived->passing-time [derived-time]
  (let [{:keys [year month day-of-month hour minute second]} derived-time]
    (i/crash abst-time "Does not need to exist - only the opposite - always have passing times, convert using opposite when need to display")))

;;
;; It might be better (going from Rich Hickey saying not to ship time around - an aside in one of his talks) not
;; to use time at all. Instead we could have a server start time and an increment based on the introduction of
;; novelty on the server. Hmm - seems impossible - or a number of seconds since server startup time - to be
;; managed by the server so as to be impervious to TZ changes and the inaccuracy of just having a ticker. Thus on
;; browser refresh we can get the latest time on the server this way. Then anything that comes from the server
;; just has a :tick-number, which can be used to calculate an actual time. Thus a TZ change that happens on the
;; client won't be noticed or need to be noticed.
;; Actually if we are going to be serious about this the day-0 time needs to be durable - kept in the DB, so the
;; incrementing :tick-number needs to be seconds since installation. Then in the DB we don't need to store
;; time at all. Thus time, when required, can always be calculated. But it will only be required by the client
;; when doing trending.
;; Server implementation. No need for quartz or anything like that. Every 5 seconds potentially find out the time.
;; Compare it to the time got last time (5 seconds ago). If it has shifted wildly then we don't use it, instead just moving
;; on 5 seconds. At the next 5 second interval things will be like normal again.
;; This way on the server there will be no need for the doubling up of values for half an hour, or missing values for
;; half an hour.
;; Thorny issue is what a query returns when is across a server TZ. That's not a thorny issue - just numbers coming
;; back. Issue is what gets displayed as time on the x axis of the graph. The answer is the TZ change will be seen.
;; Where the dots are on the graph will not change. What we need is 2 axes whenever the query goes across a TZ, so
;; the query can be seen in both TZs at once. A query over many years will be interesting - but still only 2 TZs
;; should be displayed.
;; In our case we are querying from a start time for a duration. The end time will be calculated based on the TZ at
;; that time. So the calculation start + duration = end prolly won't work.
;; When the user refreshes the browser (or logs in) all the TZ changes that have ever happened should be returned.
;; (Obviously happened since installation time). The UI will need these TZ changes to translate to what the time
;; was on the server when this measurement happened. And two alternative translations may make things clear!
;; How the translation happens is that the time the measurement happened is a number. And we also have the number
;; of TZ changes since number 0 (installation time), and the time at number 0. Incidentally we don't need to know
;; the TZ at 0. A TZ change description goes like: "at 3888444 seconds past installation time we (people who live
;; where the server lives) put our clocks on half an hour". If there's an even number of these no work needs to be
;; done.
;; Actually this s/be more general that TZ changes - any blip where time is different to what expect every 5 seconds
;; will need to be recorded. Thus we have an expected calendar and a series of blips. For instance we expect 28 days
;; in Feb when we do our calculations yet there may have been 29. So this is incorporated. So we actually have to
;; calculate the date with our approximate version of a year, in Clojurescript? YES!
;; One of the good things about this approach is there is no need to know the formal details. There are I believe
;; leap seconds etc - all will be taken account of if they happen.
;;
;(defn remote-server-time-str
;  ""
;  []
;  ())

;;
;; (js/Date.)
;; Nov 10 2015 19:09:31
;; This is what it will be when done properly - but needs to come from durable storage, and all
;; the variances need to be stored there too. Obviously time-zero needs to be before any of the
;; data samples are captured.
;; (def time-zero {:year 2015 :month "Nov" :day-of-month 10 :hour 19 :minute 9 :second 31})

;; passing-time is the number of seconds since time-zero. That's what we always use for time! This would be
;; the current passing-time, if it were not only calculated every 5 seconds.
(def seconds-past-zero (atom {:seconds-count 0}))
;(log "TIME ZERO: " time-zero)

;;
;; It is not a good idea to make this watch do anything. More than one second difference would be very
;; surprising to see. Note that TZ changes - any variances or anomolies - do not make it through to
;; here. You are seeing passing time only. The check is an ultimate sanity check - any real checking has
;; already been done.
;;
(add-watch seconds-past-zero :watcher
           (fn [key atom old-state new-state]
             (let [passing-time (:seconds-count new-state)
                   ;_ (log "True time: " passing-time)
                   derived-passing-time (passing->derived-time passing-time)
                   do-echo (= (mod passing-time 100) 0)]
               (when do-echo
                 (let [host-now (i/host-time abst-time)
                       host-derived (host->derived-time host-now)]
                   (i/log abst-time (str "passing-time: " derived-passing-time ", host-time: " host-derived)))))))

;;
;; passing-time can also be in milliseconds, in which case it will of course be the number of milliseconds that
;; have passed since zero time.
;;
(defn current-passing-millis-time []
  (let [passing-time (:seconds-count @seconds-past-zero)
        derived-passing-time (passing->derived-time passing-time)
        derived-passing-as-host (map->host-time derived-passing-time)
        host-now (i/host-time abst-time)
        host-ahead-by (- (.getTime host-now) (.getTime derived-passing-as-host))
        _ (assert (<= 0 host-ahead-by 5999))
        _ (when (>= host-ahead-by 5000) (i/log abst-time (str "Unusual to see host ahead by 5000 or more. Derived: " derived-passing-time ", Host: " host-now)))
        res (+ (* 1000 passing-time) host-ahead-by)
        ]
    res))

;;
;; This is enough to describe time down to the nearest second.
;; (Even though it is only being collected every 5 seconds)
;;
(defn current-passing-time [] (/ (current-passing-millis-time) 1000))

(defn time-zero-five-seconds-timer []
  (let [last-time-map (atom nil)
        ;; Always recording the set time, but sometimes noticing time has moved faster
        record-time (fn [new-actual-time-map]
                      (reset! last-time-map new-actual-time-map)
                      (swap! seconds-past-zero (fn [{:keys [seconds-count]}] {:seconds-count (+ seconds-count 5)}))
                      )
        in-five-seconds (partial in-n-seconds 5)]
    (go-loop [wait-time 0]
             (<! (timeout wait-time))
             (let [host-now (i/host-time abst-time)
                   now-derived (host->derived-time host-now)]
               (if (nil? @last-time-map)
                 (do
                   (reset! last-time-map now-derived)
                   (recur 5000))
                 (let [expected-in-five-seconds-map (in-five-seconds @last-time-map)
                       are-equal (= expected-in-five-seconds-map now-derived)]
                   (if are-equal
                     (do
                       (record-time expected-in-five-seconds-map)
                       (recur 5000))
                     (let [diff (host-ahead-of-map-by expected-in-five-seconds-map host-now)]
                       (i/log abst-time (str "EXPECTED: " expected-in-five-seconds-map " ACTUAL: " now-derived "\nDIFF: " diff " when been going for " (:seconds-count @seconds-past-zero)))
                       (if (and (< (abs diff) 2000) (pos? diff))  ;; Other things using the machine seem to cause this
                                                                    ;; If the process is starved so much there's > 2 seconds delay here - then we want to crash!
                         (let [for-next-time expected-in-five-seconds-map
                               advance (if (pos? diff) 1000 -1000)]
                           (record-time for-next-time)
                           (recur (- 5000 advance)))
                         (i/crash abst-time (str "Need to record a variance or anomolie because diff is -ive or > 1 second: " diff)))))))))))

;;
;; No real reason to start more or less exactly on a second, but will make reasoning easier.
;;
(defn start-timer [count]
  (let [new-host-time (i/host-time abst-time)
        millis (i/millis-component-of-host-time abst-time new-host-time)
        on-the-exact (or (= millis 999) (= millis 0))]
    (if on-the-exact
      (do
        (reset! time-zero (host->derived-time new-host-time))
        (time-zero-five-seconds-timer))
      (recur (inc count)))))

(defonce _ (start-timer 0))

;;
;; Will get a warning when run from Clojurescript but that's okay.
;;
(defn -main
  [& args]
  ;(println (host-time 2015 0 14 11 22 06))
  ;(start-timer 0)
  ;(println (stringify-time (host-time)))
  (Thread/sleep 100000)
  )
