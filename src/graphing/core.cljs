(ns graphing.core
  (:require [reagent.core :as reagent]
            [cljs.core.async :as async
             :refer [<! >! chan close!]]
            [graphing.graph-lines-db :as db]
            [graphing.utils :as u]
            [graphing.graphing :as g])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def translate-point (fn [{x :x y :y}] [x y]))

(defn mount-root []
  (g/init {:height 350 :trans-point translate-point}))

(defn ^:export run []
    (mount-root))
