(ns graphing.interop)

(defprotocol ITimeInterop
  "a protocol for calling date functions abstracted to work with clj and cljs"
  (month-as-number [this month-str])
  (host-time [this] [this ^long millis] [this year month-num day-of-month hour minute second])
  (millis-component-of-host-time [this host-time])
  (parse-int [this str])
  (stringify-time [this host-time])
  (crash [this] [this ^String msg])
  (log [this txts])
  (no-log [this txts])
  )

(def months ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"])