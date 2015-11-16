(ns graphing.interop)

(defprotocol ITimeInterop
  "a protocol for calling date functions abstracted to work with clj and cljs"
  (month-as-number [this month-str])
  (host-time [this])
  (host-time [this ^long millis])
  (host-time [this year month-num day-of-month hour minute second millis])
  (millis-component-of-host-time [this host-time])
  (parse-int [this str])
  (stringify-time [this host-time])
  (crash [this])
  (crash [this ^String msg])
  (log [this & txts])
  (no-log [this & txts])
  )

(defrecord CljTime []
  ITimeInterop
  (month-as-number [_ month-str]
    ())
  (host-time [_]
    ())
  (host-time [_ ^long millis]
    ())
  (host-time [_ year month-num day-of-month hour minute second millis]
    ())
  (millis-component-of-host-time [_ host-time]
    ())
  (parse-int [_ str]
    ())
  (stringify-time [_ host-time]
    ())
  (crash [_ ^String msg]
    ())
  (crash [_]
    ())
  (log [_ & txts]
    ())
  (no-log [_ & txts]
    ())
  )
