(ns graphing.staging-area
  (:require [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout pipe onto-chan]]
            [graphing.utils :refer [log no-log abs]]
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

;;
;; Although they say lowest and highest they actually mean lowest and highest thresholds
;;
(def gas-infos [{:name "Carbon Dioxide" :lowest 0.5 :highest 1.35}
                {:name "Carbon Monoxide" :lowest 30 :highest 55}
                {:name "Oxygen" :lowest 19 :highest 12}
                {:name "Methane" :lowest 0.25 :highest 1}])

;;
;; Given lowest and highest work out a divider so that given a change of 1 in the business
;; metric (gas for example) we can get a corresponding change in the y of this staging area.
;; Here we are assuming the staging height is 1000, and saying half that (500) should show this
;; huge change from lowest to highest. We can divide any business change by the result to get
;; the 'pixels'.
;; (By pixels I mean units of staging area - just easier to think of as pixels)
;;
(defn transition-divide-by [lowest highest]
  (let [spread (abs (- highest lowest))
        five-hundreth (/ spread 500)]
    five-hundreth))

(defn stage-ify-changer [lowest highest]
  (let [divide-num (transition-divide-by lowest highest)]
  (fn [central-y external-val]
    (let [external-over-central (- external-val central-y)
          _ (log "Above centre y in external units: " external-over-central)
          stage-val-over-central (quot external-over-central divide-num)
          stage-val (+ stage-val-over-central 499)]
      stage-val))))

;(log "Pixels position: " ((stage-ify-changer 19 12) 22 19))

;;
;; transitioner will convert an external y value into a stage y value. The resulting function will be converting one
;; point into another one. It will be used as part of a transducer which pumps values onto the trending graph
;;
(defn release-point [transitioner]
  (fn [point-map-in]
    (let [stage-value (transitioner (:val point-map-in))
          res (merge point-map-in {:y stage-value})]
      res)))

;;
;; Talking about the middle of along the x or time axis, where the middle is say the middle 10%
;;
(defn in-middle? [proportion start end]
  (let [full-length (- end start)
        half-length (/ full-length 2)
        centre-x (+ start half-length)
        half-proportion (/ (* full-length proportion) 2)
        start-middle (- centre-x half-proportion)
        end-middle (+ centre-x half-proportion)
        _ (log "start " start)
        _ (log "end " end)
        _ (log "start-middle " start-middle)
        _ (log "end-middle " end-middle)
        ]
    (fn [x]
      (and (>= x start-middle) (<= x end-middle)))))

(defn receiver [name central? out-chan in-chan]
  (let [{:keys [lowest highest]} (first (filter (fn [info] (= name (-> info :name))) gas-infos))
        _ (log name " " lowest " " highest)
        transitioner (stage-ify-changer lowest highest)]
    (go-loop [accumulated []
              release-channel nil]
             (let [data-in (<! in-chan)]
               (if (nil? release-channel)
                 (if (central? (:time data-in))
                   (let [central-y (:val data-in)
                         new-transitioner (partial transitioner central-y)
                         release-point-fn (release-point new-transitioner)
                         new-release-transducer (map release-point-fn)
                         new-release-channel (chan 1 new-release-transducer)
                         _ (pipe new-release-channel out-chan)]
                     (onto-chan new-release-channel accumulated false)
                     (>! new-release-channel data-in)
                     (recur (conj accumulated data-in) new-release-channel))
                   (recur (conj accumulated data-in) release-channel))
                 (do
                   (>! release-channel data-in)
                   (recur (conj accumulated data-in) release-channel))))))
  in-chan)

;;
;;
;;
(defn create [lines]
  ""
  (g/remove-all-lines)
  (doseq [line lines]
    (g/add-line (select-keys line line-keys))))

(defn show
  ""
  [lines start end in-chan]
  (let [out-chan (chan)
        names (map :name lines)
        receiving-chans (into {} (map (fn [name] (vector name (chan))) names))
        central? (in-middle? 0.1 start end)
        receivers (into {} (map (fn [[name chan]] (vector name (receiver name central? out-chan chan))) receiving-chans))]
    (go-loop []
             (let [latest-val (<! in-chan)
                   its-name (:name latest-val)
                   _ (no-log "name from incoming: " its-name)
                   receiving-chan (get receivers its-name)
                   _ (>! receiving-chan latest-val)])
             (recur))
    out-chan))

