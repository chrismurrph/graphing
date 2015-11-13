(ns graphing.incoming
  (:require [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [graphing.utils :refer [log]]
            [graphing.known-data-model :as db]
            [graphing.graphing :as g]
            [graphing.passing-time :as et]
            [graphing.utils :as u])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;;
;; Whenever its out channel is not blocked it will be generating a new gas value
;;
(defn generator [start end name out-chan]
  (go-loop []
    ;(log "In generator")
    (>! out-chan (str "Hi from " name))
    (recur)))

;;
;; Just needs the channels it is going to get values from
;;
(defn controller [out-chan chans]
  (log (seq chans))
  (go-loop []
    (<! (timeout 300))
    ;(log "In controller")
    (let [chan-idx (rand-int (count chans))
          chan (nth chans chan-idx)
          next-val (<! chan)
          _ (>! out-chan next-val)]
      ;(log "Waiting from " chan-idx)
      ;(log (<! chan))
      (recur))
  ))

(defn query-remote-server
  "Just needs the names that are to be queried and start/end times"
  [names start end]
  (let [new-gen (partial generator start end)
        out-chan (chan)
        gas-channels (into {} (map (fn [name] (vector name (chan))) names))
        _ (log gas-channels)
        _ (controller out-chan (vals gas-channels))
        _ (mapv (fn [[name chan]] (new-gen name chan)) gas-channels)
        ]
    out-chan
    )
  )

;;
;; Directly puts dots on the screen. Really it is staging-area's job to do this intelligently. So this will go.
;;
(def tick-timer
  (let [already-gone (fn [already-sent name x] (some #{{:line-name name :x x}} already-sent))]
    (go-loop [already-sent []]
             (<! (timeout 1000))
             ;(log "In timer")
             (let [line-num (rand-int 3)
                   line (nth @db/lines line-num)
                   name (:name line)
                   line-size (count (:positions line))
                   chosen-idx (rand-int line-size)
                   position (nth (:positions line) chosen-idx)
                   chosen-x-pos (:x position)
                   has-gone (already-gone already-sent name chosen-x-pos)
                   ]
               (if has-gone
                 (recur already-sent)
                 (do
                   ;(log name " at " position " about to go... ")
                   (g/add-point-by-sa {:name name :point [chosen-x-pos (:y position) (:val position)]})
                   (recur (conj already-sent {:line-name name :x chosen-x-pos}))))
               ))))