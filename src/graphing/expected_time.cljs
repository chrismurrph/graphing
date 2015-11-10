(ns graphing.expected-time
  (:require [graphing.utils :refer [log]]
            [clojure.string :as str]))

;;
;; Nov 10 2015 19:09:31
;;
(defn parse-time
  [js-time]
  (log "IN:" js-time)
  (let [[_ month day-of-month year time-str] (str/split js-time " ")
        [hour min sec] (str/split time-str ":")]
    ;(log month " " day-of-month " " year " " hour " " min " " sec)
    {:year (int year) :month month :day-of-month (int day-of-month) :hour (int hour) :minute (int min) :second (int sec)}))

(def last-day-of-months {"Jan" 31 "Feb" 28 "Mar" 31 "Apr" 30 "May" 31 "Jun" 30 "Jul" 31 "Aug" 31 "Sep" 30 "Oct" 31 "Nov" 30 "Dec" 31})
(def months (keys last-day-of-months))

;;
;; Returns nil if we are at the end of the year, so caller knows to increment the year as well as
;; setting the month to "Jan"
;;
(defn get-next-month [in-month]
  (if (= in-month "Dec")
    nil
    (nth months (inc (.indexOf months in-month)))))

(defn in-five-seconds
  "Following standard conventions, what do we expect the time to be in 5 seconds from the given time"
  [in-time-map]
  (let [{:keys [year month day-of-month hour minute second]} in-time-map
        five-more-at-the-top (fn [in-seconds] (- (+ in-seconds 5) 60))]
    (if (< second 55)
      (merge in-time-map {:second (+ 5 second)})
      (if (< minute 59)
        (merge in-time-map {:minute (inc minute) :second (five-more-at-the-top second)})
        (if (< hour 23)
          (merge in-time-map {:hour (inc hour) :minute 0 :second (five-more-at-the-top second)})
          (let [max-day (month last-day-of-months)]
            (if (< day-of-month max-day)
              (merge in-time-map {:day-of-month (inc day-of-month) :hour 0 :minute 0 :second (five-more-at-the-top second)})
              (let [next-month (get-next-month month)]
                (if (nil? next-month)
                  {:year (inc year) :month "Jan" :day-of-month 1 :hour 0 :minute 0 :second (five-more-at-the-top second)}
                  (merge in-time-map {:month next-month :day-of-month 1 :hour 0 :minute 0 :second (five-more-at-the-top second)}))))))))))