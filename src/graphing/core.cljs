(ns graphing.core
  (:require [reagent.core :as reagent]
            [cljs.core.async :as async
             :refer [<! >! chan close!]]
            [graphing.graph-lines-db :as db]
            [graphing.utils :as u]
            [graphing.graphing :as g])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def height 480)

(defn mount-root []
  (g/init))

(defn ^:export run []
    (mount-root))
