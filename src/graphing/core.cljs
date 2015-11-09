(ns graphing.core
  (:require [graphing.known-data-model :as db]
            [graphing.graphing :as g]
            [graphing.utils :refer [log]]
            [graphing.staging-area :as sa]
            [graphing.incoming :as in])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def graph-width 640)
(def graph-height 250)

(defn insert-known-points []
  (doseq [line @db/lines]
    (let [line-name (:name line)]
      (doseq [position (:positions line)]
        (g/add-point-by-sa {:name line-name :point [(:x position) (:y position) (:val position)]})))))

(defn mount-root []
  (g/init {:height graph-height :width graph-width})
  (let [line-names (map :name @db/lines)
        chan (in/query-remote-server line-names "" "")
        _ (sa/create @db/lines chan)])
  ; Let incoming do this gradually
  ;(insert-known-points)
  )

(defn ^:export run []
    (mount-root))
