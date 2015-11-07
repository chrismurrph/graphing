(ns graphing.incoming
  (:require [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [graphing.utils :refer [log]]
            [graphing.graph-lines-db :as db]
            [graphing.graphing :as g])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn already-gone [already-sent name x]
  (some #{{:line-name name :x x}} already-sent))

;;
;; Temporarily make it a definition so it never gets called!
;;
(defn tick-timer []
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
