(ns graphing.core
  (:require [graphing.graph-lines-db :as db]
            [graphing.graphing :as g]
            [graphing.utils :refer [log]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;
;; With these the functions get-positions, get-colour and translate-point
;; the library is being told how to access the data you
;; will be giving it.
;;
(def get-positions :positions)
(def get-colour :colour)
(def get-units :units)
(def graph-width 640)
(def graph-height 250)

;;
;; Translation is always business to screen, not the other way round
;;
(def horizontally-translate (db/scale-fn graph-width))
(def vertically-translate (db/scale-fn graph-height))

;;
;; The x and y coming in here are in 'our' co-ordinate system (i.e. not the graph's one). The scale function already
;; knows about 'our' co-ordinate system. And the two functions horizontally-translate and vertically-translate know
;; how to scale across from one system to another, going TO the graph's geometry. Our geometry happens to be 0-999
;; for both x and y. You can only see that if you look at the source of db.
;;
(def translate-point (fn [{x :x y :y val :val}] [(horizontally-translate x) (vertically-translate y) val]))
(def translator {:vertically vertically-translate :horizontally horizontally-translate :whole-point translate-point})

(defn mount-root []
  (g/init {:height graph-height :width graph-width :translator translator :get-positions get-positions :get-colour get-colour :get-units get-units}))

(defn ^:export run []
    (mount-root))
