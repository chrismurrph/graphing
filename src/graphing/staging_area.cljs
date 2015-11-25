(ns graphing.staging-area
  (:require [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [graphing.utils :refer [log no-log]]
            [graphing.known-data-model :refer [light-blue green pink]]
            [graphing.graphing :as g]
            [graphing.known-data-model :as db])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;;
;; The x and y coming in here are in 'our' co-ordinate system (i.e. not the graph's one). The scale function already
;; knows about 'our' co-ordinate system. And the two functions horizontally-translate and vertically-translate know
;; how to scale across from one system to another, going TO the graph's geometry. Our geometry happens to be 0-999
;; for both x and y. You can only see that if you look at the source of db.
;;
;(def translate-point (fn [{x :x y :y val :val}] [(horizontally-translate x) (vertically-translate y) val]))
;(def translator {:vertically vertically-translate :horizontally horizontally-translate :whole-point translate-point})
(def line-keys [:name :units :colour :dec-places])

(defn receiver [name out-chan in-chan]
  (go-loop []
    (let [data-in (<! in-chan)]
      (log name " JUST GOT " data-in)
      (recur)))
  in-chan)

;;
;;
;;
(defn create [lines in-chan]
  ""
  (g/remove-all-lines)
  (doseq [line lines]
    (g/add-line (select-keys line line-keys)))
  (let [out-chan (chan)
        names (map :name lines)
        receiving-chans (into {} (map (fn [name] (vector name (chan))) names))
        receivers (into {} (map (fn [[name chan]] (vector name (receiver name out-chan chan))) receiving-chans))]
    (go-loop []
      (let [latest-val (<! in-chan)
            its-name (:name latest-val)
            _ (no-log "name from incoming: " its-name)
            receiving-chan (get receivers its-name)
            _ (>! receiving-chan latest-val)])
      (recur))
    out-chan))

(defn query
  ""
  [start end]
  )

