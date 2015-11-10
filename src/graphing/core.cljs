(ns graphing.core
  (:require [graphing.known-data-model :as db]
            [graphing.graphing :as g]
            [graphing.utils :refer [log]]
            [graphing.staging-area :as sa]
            [graphing.incoming :as in])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def graph-width 640)
(def graph-height 250)

;;
;; All dates should come from the server originally and there should be 'no timezone' kept on the client. Achieve this
;; by constructing time from Strings that come from the server. Even 'now' can be constructed by knowing the
;; difference (found out when browser is refreshed) between the time on the server and the time on this client.
;; There could be confusion when a timezone change happens while user is logged on. A browser refresh will fix that.
;; This is the known problem we will have to live with. TZ changes are handled quite well on the server. See doco
;; there... If the client is in the same TZ then a browser refresh won't be needed (unless the local time is
;; grabbed at one of the moments that the clocks are out of sync).
;; 'no timezone' - we can't use js LocalDate as we need to span days. And otherwise dates in js have a TZ component
;; that can't be escaped. So a 'time shift' will occur.
;; Way to fix all issues is to know when a time-shift has occured on the server or the client and at that point
;; re-calculating the 'difference between server and client times - cient-fast-by'. When this message occurs all
;; current queries need to be obliterated - so the points on the graph the user is looking at will vanish before the
;; user's eyes.
;; TODO Make a call to incoming/remote-server-time-str on server refresh and implement client-fast-by
;; ALSO How would you get an interrupt in a JS client when a TZ change happens?
;;
;(def start (ti/date-time 1986 10 14 4 3 27))
;(def end (ti/plus start (ti/months 1) (ti/weeks 3)))

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
