(ns graphing.interop)

(defprotocol ITimeInterop
  "a protocol for calling date functions abstracted to work with clj and cljs"
  (month-as-number [this month-str])
  (host-time [this] [this millis] [this year month-num day-of-month hour minute second])
  (millis-component-of-host-time [this host-time])
  (parse-to-int [this str])
  (format-from-time [this host-time])
  (crash [this] [this msg])
  (log [this txt])
  (no-log [this txt])
  )

(def months ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"])