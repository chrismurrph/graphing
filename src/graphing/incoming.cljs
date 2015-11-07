(ns graphing.incoming
  (:require [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [graphing.utils :refer [log]]
            )
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def tick-timer
  (go-loop []
    (<! (timeout 300))
    (log "In timer")
    (recur)))
