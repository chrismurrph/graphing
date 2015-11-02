(ns graphing.core
  (:require [reagent.core :as reagent]
            [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]]
            [cljs.core.match]
            [graphing.graph-lines-db :as db]
            [graphing.utils :as u])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [cljs.core.match.macros :refer [match]]))

(enable-console-print!)
(def ratom reagent/atom)

(def uniqkey (atom 0))
(defn gen-key [] 
  (let [res (swap! uniqkey inc)]
    ;(u/log res)
    res))

(def first-line [[10 10] [20 20] [30 30] [40 40] [50 50]])
(def height 480)
(defn now-time [] (js/Date.))
(defn seconds [js-date] (.getSeconds js-date))

(defn still-interested? [past-time current-time]
  (let [diff (- (seconds current-time) (seconds past-time))]
    (< diff 10)))

;;
;; When in sticky time we want mouse movement to be ignored.
;; Thus if user drags to a place and leaves it there for a second, he can then move the cursor out of the way
;; A further refinement would be for the moving away to make it 'stuck'
;; (and clicking would also have to have this effect)
;;
(defn in-sticky-time? [past-time current-time]
  (if (or (nil? current-time) (nil? past-time))
    false
    (let [diff (- (seconds current-time) (seconds past-time))]
      (< 1 diff 4))))

(def point-defaults
  {:stroke "black"
   :stroke-width 2
   :r 5})

(def segment-defaults
  {:stroke "black"
   :stroke-width 2})

(defn segment [height visible x-position]
  (let [from [x-position 0]
        to [x-position height]
        res (when visible [:line
                           (merge segment-defaults
                                  {:x1 (first from) :y1 (second from)
                                   :x2 (first to) :y2 (second to)})])
        ;_ (when res (u/log res))
        ]
    res))

;;
;; Need to supply visible and x-position to display it
;;
(def hover-line-at (partial segment height))

;;
;; Creates a point as a component
;;
(defn point [fill x y]
  [:circle
   (merge point-defaults
          {:cx x
           :cy y
           :fill fill})])

(defn old-controller [inchan state-ref]
  (go-loop [cur-x nil cur-y nil mouse-state :up]
    (match [(<! inchan) mouse-state]
           [({:type "mousedown" :x x :y y} :as e) :up]
           (do
             (recur x y :down))

           [({:type "mousemove" :x x :y y} :as e) :down]
           (do
             (swap! state-ref update-in [:current-path] conj {:x x :y y})
             (recur x y :down))

           [({:type "mouseup" :x x :y y} :as e) :down]
           (do
             (swap! state-ref (fn [{:keys [current-path paths] :as state}]
                                (assoc state :paths (conj paths current-path) :current-path [])))
             (recur x y :up))

           [s e] (recur cur-x cur-y mouse-state))))

(def state (ratom {:my-lines [first-line] :hover-pos nil :last-mouse-moment nil}))
(def current-line (count (:my-lines @state)))

(defn new-controller [inchan state-ref]
  (go-loop [cur-x nil cur-y nil]
           (match [(<! inchan)]

                  [{:type "mousemove" :x x :y y}]
                  (let [now-moment (now-time)
                        previous-mouse-moment (:last-mouse-moment @state-ref)]
                    ;(u/log "Moved to " x)
                    (when (not (in-sticky-time? previous-mouse-moment now-moment))
                      (swap! state-ref assoc-in [:hover-pos] x)
                      (swap! state-ref assoc-in [:last-mouse-moment] now-moment)
                      ;(u/log (get-in @state-ref [:hover-pos]))
                      )
                    (recur x y))

                  [{:type "mouseup" :x x :y y}]
                  (do
                    (swap! state-ref update-in [:my-lines current-line] (fn [coll-at-n] (vec (conj coll-at-n [x y]))))
                    ;(u/log "When mouse up time is: " when-last-moved)
                    (recur x y))

                  [_]
                  (do
                    (recur cur-x cur-y)))))

(def controller new-controller)

(defn event-handler-fn [comms component e]
  (let [bounds (. (reagent/dom-node component) getBoundingClientRect)
        y (- (.-clientY e) (.-top bounds))
        x (- (.-clientX e) (.-left bounds))]
     (put! comms {:type (.-type e) :x x :y y})
     nil))

(defn point-component [fill [x y]]
  ^{:key (gen-key)} [point fill x y])

(defn all-points-component [my-lines]
  (into [:g {:key (gen-key)}]
        (map #(point-component nil %) (mapcat identity my-lines))))

(defn hover-visible? [last-mouse-moment now-moment]
  (if (nil? last-mouse-moment)
    false
    (still-interested? last-mouse-moment now-moment)))

(defn tick []
  ;(u/log "TICK")
  (let [now (now-time)
        should-be-visible (hover-visible? (:last-mouse-moment @state) now)]
    ;(u/log should-be-visible " at " now)
    (when (not should-be-visible)
      (swap! state assoc-in [:hover-pos] nil)
      (swap! state assoc-in [:last-mouse-moment] nil)))
  )

(defonce time-updater (js/setInterval #(tick) 100))

(defn read-in-line [name]
  (u/log name)
  (let [line (db/get-line name)
        colour (:colour line)
        positions (:positions line)
        mapify-fn (fn [{x :x y :y}] [x y])
        mapped-in (mapv mapify-fn positions)
        count-existing-lines (count (:my-lines @state))]
    (u/log mapped-in " where are already " count-existing-lines)
    (swap! state assoc-in [:my-lines count-existing-lines] mapped-in)
    (swap! state assoc-in [:my-lines count-existing-lines] colour)))

(defn trending-app [{:keys [state-ref comms] :as props}]
  (let [{:keys [my-lines hover-pos last-mouse-moment]} @state-ref
        component (reagent/current-component)
        handler-fn (partial event-handler-fn comms component)
        ]
    [:div
     [:svg {:height height :width 640
            :on-mouse-up handler-fn :on-mouse-down handler-fn :on-mouse-move handler-fn
            :style {:border "thin solid black"}}
      [all-points-component my-lines]
      [hover-line-at (not (nil? hover-pos)) hover-pos]]
     [:input {:type "button" :value "Methane"
              :on-click (partial read-in-line "Methane")}]
     [:input {:type "button" :value "Oxygen"
              :on-click (partial read-in-line "Oxygen")}]]
  ))

(defn mount-root []
  (let [paths-ratom state
        ch (chan)
        proc (controller ch paths-ratom)]
    (reagent/render-component
       [trending-app {:state-ref paths-ratom :comms ch}]
       (.-body js/document))
    (go
      (let [exit (<! proc)]
        (prn :exit! exit)))))      

(defn ^:export run []
    (mount-root))
