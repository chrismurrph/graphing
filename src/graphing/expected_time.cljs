(ns graphing.expected-time
  (:require [graphing.utils :refer [log]]
            [clojure.string :as str]
            [graphing.utils :as u]))

;;
;; new Date("October 13, 2014 11:13:00") should work in js
;; Doesn't however
;;
(defn format-time
  [time-map]
  (let [{:keys [year month day-of-month hour minute second]} time-map
        res (str month " " day-of-month ", " year " " hour ":" minute ":" second)]
    res))

(def last-day-of-months {"Jan" 31 "Feb" 28 "Mar" 31 "Apr" 30 "May" 31 "Jun" 30 "Jul" 31 "Aug" 31 "Sep" 30 "Oct" 31 "Nov" 30 "Dec" 31})

(def months ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"])
(defn month-as-number [month-str] (.indexOf (to-array months) month-str))

(defn add-seconds [js-time seconds]
  (let [given-millis (.getTime js-time)
        augmented-millis (+ (* seconds 1000) given-millis)
        res (js/Date. augmented-millis)]
    res))

;(defn add-seconds
;  ([s] (add-seconds (js/Date.) s))
;  ([d s] (js/Date. (+ (.getTime d) (* 1000 s)))))

(def now (js/Date.))
(def plus-20 (add-seconds now 20))
(log "Now is " now)
(log "20 secs time is " plus-20)

(defn host-time-from-map [time-map]
  (let [{:keys [year month day-of-month hour minute second]} time-map
        host-time (js/Date. year (month-as-number month) day-of-month hour minute second)
        ]
    host-time))

(defn host-add-seconds [host-time seconds]
  )

;;
;; When this function is called there is a known difference of at least half a second. This function returns the
;; difference in milliseconds. Tiny drifts can be adjusted for. Anything else is either a variance or an anomolie.
;; If the return value is +ive then current time has raced ahead, which by convention is what we are intending to
;; happen by making the async wait time exactly five seconds.
;;
;; new Date(year, month, day, hours, minutes, seconds, milliseconds)
;;
(defn diff [expected-map js-time]
  (let [expected-time (host-time-from-map expected-map)
        ;_ (log "expected-time: " expected-time)
        diff (- (.getMilliseconds js-time) (.getMilliseconds expected-time))
        ;_ (log "Amount that current: " current-time " is more than expected: " expected-time ", is: " diff)
        ;_ (assert (>= (u/abs diff) 500) (str "diff not >= 500, but: " diff))
        ]
    diff))

;;
;; Returns nil if we are at the end of the year, so caller knows to increment the year as well as
;; setting the month to "Jan"
;;
(defn get-next-month [in-month]
  (if (= in-month "Dec")
    nil
    (nth months (inc (month-as-number in-month)))))

(defn in-n-seconds
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

;;
;; What comes in is (js/Date.), yet here we interpret it as a String, and call .getMilliseconds on it. There must
;; be hidden conversion to String.
;; example
;; Nov 10 2015 19:09:31
;;
(defn parse-time
  [js-time]
  (log "IN:" js-time)
  (let [millis (.getMilliseconds js-time)
        [_ month day-of-month year time-str] (str/split js-time " ")
        [hour min sec] (str/split time-str ":")
        seconds (int sec)
        b4-rounding {:year (int year) :month month :day-of-month (int day-of-month) :hour (int hour) :minute (int min) :second seconds}
        more-than-half-way-to-next (>= millis 500)]
    ;(log month " " day-of-month " " year " " hour " " min " " sec)
    (if more-than-half-way-to-next
      (in-n-seconds 1 b4-rounding)
      b4-rounding)
    ))
