(ns graphing.core
  (:require [reagent.core :as reagent]
            [cljs.core.async :as async
             :refer [<! >! chan close!]]
            [graphing.graph-lines-db :as db]
            [graphing.utils :as u]
            [graphing.graphing :as g])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;
;; With these three functions the library is being told how to access the data you
;; will be giving it.
;;
(def get-positions :positions)
(def get-colour :colour)
(def translate-point (fn [{x :x y :y}] [x y]))

(defn mount-root []
  (g/init {:height 450 :trans-point translate-point :get-positions get-positions :get-colour get-colour}))

(defn ^:export run []
    (mount-root))
