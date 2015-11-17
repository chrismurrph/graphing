(ns graphing.cljs-interop
  (:require [clojure.string :as str]
            [graphing.interop :as i]))

(defn crash
  ([^String msg]
   (throw (js/Error. msg))
    )
  ([]
   (crash "Purposeful crash"))
  )

(defrecord CljsTime []
  i/ITimeInterop
  (month-as-number [_ month-str]
    (.indexOf (to-array i/months) month-str))
  (host-time [_]
    (js/Date.))
  (host-time [_ millis]
    (js/Date. millis))
  (host-time [_ year month-num day-of-month hour minute second]
    (js/Date. year month-num day-of-month hour minute second))
  (millis-component-of-host-time [_ host-time]
    (.getMilliseconds host-time))
  (parse-to-int [_ str]
    (int str))
  ;;
  ;; example
  ;; Nov 10 2015 19:09:31
  ;; MM dd yyyy HH:mm:ss
  ;;
  (format-from-time [_ host-time]
    (let [[_ month day-of-month year time-str] (str/split host-time #" ")
          [hour min sec] (str/split time-str #":")]
      {:month month :day-of-month day-of-month :year year :hour hour :min min :sec sec}))
  (crash [_ msg]
    (throw (throw (js/Error. msg))))
  (crash [this]
    (i/crash this "Purposeful crash"))
  (log [_ txt]
    (.log js/console txt))
  (no-log [_ txt]
    ())
  )
