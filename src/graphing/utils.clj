(ns graphing.utils)

(defn log [& txts]
  (println (apply str txts)))

(defn no-log [& txts]
  ())

(defn crash
  ([^String msg]
   (throw (Throwable. msg)))
  ([]
   (crash "Purposeful crash"))
  )

(defn parse-int [s]
  (Integer. (re-find #"[0-9]*" s)))

(defn abs [val]
  (if (neg? val)
    (* -1 val)
    val))

(defmacro spy [x]
  `(let [x# ~x]
     (println '~x "=>" x#)
     x#))
