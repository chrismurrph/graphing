(ns graphing.incoming
  (:require [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [graphing.utils :refer [log]]
            [graphing.known-data-model :as db]
            [graphing.graphing :as g])
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
;;
(defn remote-server-time-str "")

;;
;; Directly puts dots on the screen. Really it is staging-area's job to do this intelligently. So this will go.
;;

(defn already-gone [already-sent name x]
  (some #{{:line-name name :x x}} already-sent))

;;
;; Temporarily make it a definition when want to make sure it never gets called!
;;
(def tick-timer
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
      )))
