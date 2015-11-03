(ns graphing.core
  (:require [reagent.core :as reagent]
            [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]]
            [cljs.core.match]
            [graphing.graph-lines-db :as db]
            [graphing.utils :as u])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [cljs.core.match.macros :refer [match]]))

(def ratom reagent/atom)

(def uniqkey (atom 0))
(defn gen-key [] 
  (let [res (swap! uniqkey inc)]
    ;(u/log res)
    res))

(def first-line {:name "First line" :colour db/light-blue :points [[10 10] [20 20] [30 30] [40 40] [50 50]]})
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
  {:stroke (u/rgb-map-to-str db/black)
   :stroke-width 2
   :r 5})

(def segment-defaults
  {:stroke (u/rgb-map-to-str db/black)
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
(defn point [rgb-map x y]
  (u/log rgb-map)
  [:circle
   (merge point-defaults
          {:cx x
           :cy y
           :fill (u/rgb-map-to-str rgb-map)})])

(def state (ratom {:my-lines [first-line] :hover-pos nil :last-mouse-moment nil}))
(defn future-line [] (count (:my-lines @state)))

(defn controller [inchan state-ref]
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
                  (let [current-line (dec (future-line))]
                    (u/log "Already colour of current line at " current-line " is " (get-in @state-ref [:my-lines current-line :colour]))
                    (swap! state-ref update-in [:my-lines current-line :points] (fn [points-at-n] (vec (conj points-at-n [x y]))))
                    ;(u/log "When mouse up time is: " when-last-moved)
                    (recur x y))

                  [_]
                  (do
                    (recur cur-x cur-y)))))

(defn event-handler-fn [comms component e]
  (let [bounds (. (reagent/dom-node component) getBoundingClientRect)
        y (- (.-clientY e) (.-top bounds))
        x (- (.-clientX e) (.-left bounds))]
     (put! comms {:type (.-type e) :x x :y y})
     nil))

(defn point-component [rgb-map [x y]]
  ^{:key (gen-key)} [point rgb-map x y])

(defn points-from-lines [my-lines]
  (for [line my-lines
        :let [colour (:colour line)
              _ (u/log "Colour of " (:name line) " is " colour)
              component-fn (partial point-component colour)
              points (:points line)]
        point points
        :let [component (component-fn point)]]
    component))

(defn all-points-component [my-lines]
  (into [:g {:key (gen-key)}]
        ;(map #(point-component nil %) (mapcat identity my-lines))
        (points-from-lines my-lines)
        ))

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
        mappify-fn (fn [{x :x y :y}] [x y])
        mapped-in (mapv mappify-fn positions)
        count-existing-lines (count (:my-lines @state))]
    (u/log mapped-in " where are already " count-existing-lines " and new colour: " colour)
    (swap! state assoc-in [:my-lines count-existing-lines :points] mapped-in)
    (swap! state assoc-in [:my-lines count-existing-lines :colour] colour)))

(defn trending-app [{:keys [state-ref comms] :as props}]
  (let [{:keys [my-lines hover-pos]} @state-ref
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
