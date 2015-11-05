(ns graphing.graphing
  (:require [reagent.core :as reagent]
            [cljs.core.async :as async
             :refer [<! >! chan close! put!]]
            [graphing.graph-lines-db :refer [light-blue black get-external-line enclosed-by]]
            [graphing.utils :refer [log distance bisect-vertical-between]]
            )
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [cljs.core.match.macros :refer [match]]))

(def ratom reagent/atom)

(def first-line {:name "First line" :colour light-blue :points [[10 10] [20 20] [30 30] [40 40] [50 50]]})

(defn rgb-map-to-str [{r :r g :g b :b}]
  (str "rgb(" r "," g "," b ")"))

(def uniqkey (atom 0))
(defn gen-key []
  (let [res (swap! uniqkey inc)]
    ;(u/log res)
    res))

(defn now-time [] (js/Date.))
(defn seconds [js-date] (.getSeconds js-date))

;;
;; Currently leaving up forever. Will prolly re-instate use of this later...
;;
(defn still-interested? [past-time current-time]
  (let [diff (- (seconds current-time) (seconds past-time))]
    (< diff 30)))

(def point-defaults
  {:stroke (rgb-map-to-str black)
   :stroke-width 2
   :r 5})

(def line-defaults
  {:stroke (rgb-map-to-str black)
   :stroke-width 1})

(defn plum-line [height visible x-position]
  (let [from [x-position 0]
        to [x-position height]
        res (when visible [:line
                           (merge line-defaults
                                  {:x1 (first from) :y1 (second from)
                                   :x2 (first to) :y2 (second to)})])]
    res))

;;
;; Many lines coming out from the plum line
;;
(defn tick-lines [x drop-distances]
  (into [:g]
        (for [drop-distance drop-distances
              :let [from [x drop-distance]
                    to [(+ x 10) drop-distance]
                    res [:line
                         (merge line-defaults
                                {:x1 (first from) :y1 (second from)
                                 :x2 (first to) :y2 (second to)})]]]
          res)))

;;
;; Need to supply visible and x-position to display it
;;
(defn plum-line-at [height] (partial plum-line height))

;;
;; At the time that the plum-line is made visible, many of these at the same x will also be
;; made visible
;;
(defn tick-lines-over [x] (partial tick-lines x))

;;
;; Creates a point as a component
;;
(defn point [rgb-map x y]
  (log rgb-map)
  [:circle
   (merge point-defaults
          {:cx x
           :cy y
           :fill (rgb-map-to-str rgb-map)})])

(def state (ratom {:my-lines [first-line] :hover-pos nil :last-mouse-moment nil :in-sticky-time? false :labels-visible? false :labels []}))
(defn my-lines-size [] (count (:my-lines @state)))

(defn controller [inchan]
  (go-loop [cur-x nil cur-y nil old-x nil old-y nil]
           (match [(<! inchan)]

                  [{:type "mousemove" :x x :y y}]
                  (let [now-moment (now-time)
                        in-sticky-time? (:in-sticky-time? @state)
                        diff (distance [old-x old-y] [cur-x cur-y])
                        is-flick (> diff 10)]
                    (when (not is-flick)
                      (when (not in-sticky-time?)
                        (swap! state assoc-in [:hover-pos] x)
                        (swap! state assoc-in [:last-mouse-moment] now-moment)
                        (swap! state assoc-in [:labels-visible?] false)
                        ;(u/log (get-in @state-ref [:hover-pos]))
                        ))
                    (recur x y cur-x cur-y))

                  [{:type "mouseup" :x x :y y}]
                  (let [current-line (dec (my-lines-size))]
                    (log "Already colour of current line at " current-line " is " (get-in @state [:my-lines current-line :colour]))
                    (swap! state update-in [:my-lines current-line :points] (fn [points-at-n] (vec (conj points-at-n [x y]))))
                    ;(u/log "When mouse up time is: " when-last-moved)
                    (recur x y old-x old-y))

                  [_]
                  (do
                    (recur cur-x cur-y old-x old-y)))))

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
              _ (log "Colour of " (:name line) " is " colour)
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

;(defn hover-visible? [last-mouse-moment now-moment]
;  (if (nil? last-mouse-moment)
;    false
;    (still-interested? last-mouse-moment now-moment)))

(defn get-names []
  (map :name (get-in @state [:my-lines])))

(defn show-labels-moment [translator x]
  (let [names (get-names)
        _ (log "names: " names)
        surrounding-at (partial enclosed-by (:horizontally translator) x)
        trans-point-fn (:whole-point translator)
        enclosed-by-res (remove nil? (map surrounding-at names))
        _ (log "enclosed result: " enclosed-by-res)
        y-intersects (for [[left-of right-of] enclosed-by-res
                           :let [left-translated (trans-point-fn left-of)
                                 right-translated (trans-point-fn right-of)
                                 _ (log "left: " left-translated)
                                 _ (log "right: " right-translated)
                                 _ (log "x: " x)
                                 y-intersect (bisect-vertical-between left-translated right-translated x)]]
                       y-intersect)]
    (vec y-intersects)))

;;
;; When in sticky time we want mouse movement to be ignored.
;; Thus if user drags to a place and leaves it there for a second, he can then move the cursor out of the way
;; A further refinement would be for the moving away to make it 'stuck'
;; (and clicking would also have to have this effect)
;;
(defn tick [translator]
  (let [in-sticky-time? (fn [past-time current-time]
                          (if (or (nil? current-time) (nil? past-time))
                            false
                            (let [diff (- (seconds current-time) (seconds past-time))]
                              (< 1 diff 4))))
        show-labels-fn (partial show-labels-moment translator)]
  (fn []
    (let [now (now-time)
          last-time-moved (:last-mouse-moment @state)
          currently-sticky (get-in @state [:in-sticky-time?])
          now-sticking (in-sticky-time? last-time-moved now)
          x (get-in @state [:hover-pos])
          labels-already-showing (get-in @state [:labels-visible?])]
      (if (not currently-sticky)
        (when now-sticking
          (when (not labels-already-showing)
            (swap! state assoc-in [:labels] (show-labels-fn x))
            (swap! state assoc-in [:labels-visible?] true)
            (swap! state assoc-in [:in-sticky-time?] true)))
        (when (not now-sticking)
          (swap! state assoc-in [:in-sticky-time?] false)))))))

(defn read-in-external-line [mappify-point-fn get-positions get-colour name]
  (log name)
  (let [line (get-external-line name)
        colour (get-colour line)
        positions (get-positions line)
        mapped-in (mapv mappify-point-fn positions)
        count-existing-lines (count (:my-lines @state))
        new-line {:name name :points mapped-in :colour colour}]
    (log mapped-in " where are already " count-existing-lines " and new colour: " colour)
    (swap! state assoc-in [:my-lines count-existing-lines] new-line)))

(defn main-component [options-map]
  (let [{:keys [handler-fn my-lines hover-pos labels-visible? height width translator get-positions get-colour],
         :or {height 480 width 640}} options-map
        line-reader (partial read-in-external-line (-> translator :whole-point) get-positions get-colour)]
    [:div
     [:svg {:height height :width width
            :on-mouse-up handler-fn :on-mouse-down handler-fn :on-mouse-move handler-fn
            :style {:border "thin solid black"}}
      [all-points-component my-lines]
      [(plum-line-at height) (not (nil? hover-pos)) hover-pos]
      (when labels-visible?
        [(tick-lines-over hover-pos) (get-in @state [:labels])])
      ]
     [:input {:type "button" :value "Methane"
              :on-click #(line-reader "Methane")}]
     [:input {:type "button" :value "Oxygen"
              :on-click #(line-reader "Oxygen")}]
     ]))

(defn trending-app [options-map]
  (let [component (reagent/current-component)
        handler-fn (partial event-handler-fn (:comms options-map) component)
        args (into (into {:handler-fn handler-fn} @state) (dissoc options-map :state-ref :comms))]
    [main-component args]))

;;
;; keyword options:
;; :height :width :trans-point :get-positions :get-colour
;; All are defaulted - see main-component
;; Note that :trans-colour does not exist - colours have to be of shape {:r :g :b}
;;
(defn init [options-map]
  (let [ch (chan)
        proc (controller ch)
        args (into {:comms ch} options-map)
        tick-with-trans (tick (:translator options-map))
        _ (js/setInterval #(tick-with-trans) 500)
        ]
    (reagent/render-component
      [trending-app args]
      (.-body js/document))
    (go
      (let [exit (<! proc)]
        (prn :exit! exit)))))