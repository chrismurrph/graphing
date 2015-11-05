(ns graphing.graphing
  (:require [reagent.core :as reagent]
            [cljs.core.async :as async
             :refer [<! >! chan close! put!]]
            [graphing.graph-lines-db :refer [light-blue black get-external-line]]
            [graphing.utils :refer [log distance bisect-vertical-between]]
            [goog.string :as gstring]
            [goog.string.format]
            )
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [cljs.core.match.macros :refer [match]]))

(def ratom reagent/atom)

;;
;; [x y real-value]
;; Note that y is greater as lower but real value will be smaller as lower
;;
(def first-line {:name "First line" :colour light-blue :dec-places 3 :units "" :points [[10 10 0.511] [20 20 0.411] [30 30 0.311] [40 40 0.211] [50 50 0.111]]})

(defn rgb-map-to-str [{r :r g :g b :b}]
  (str "rgb(" r "," g "," b ")"))

(defn format-as-str [dec-pl val]
  (gstring/format (str "%." dec-pl "f") val))

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
  (let [res (when visible [:line
                           (merge line-defaults
                                  {:x1 x-position :y1 0
                                   :x2 x-position :y2 height})])]
    res))

(defn insert-labels [x drop-infos]
  (for [drop-info drop-infos
        :let [y-intersect (:y-intersect drop-info)]]
    ^{:key (gen-key)}[:text {:x (+ x 10) :y (+ (:proportional-y y-intersect) 4) :font-size "0.8em"} (format-as-str (or (:dec-places y-intersect) 2) (:proportional-val y-intersect))]))

;;
;; Many lines coming out from the plum line
;;
(defn tick-lines [x drop-infos]
  (log "info: " drop-infos)
  (into [:g (insert-labels x drop-infos)]
        (for [drop-info drop-infos
              :let [drop-distance (:proportional-y (:y-intersect drop-info))
                    res [:line
                         (merge line-defaults
                                {:x1 x :y1 drop-distance
                                 :x2 (+ x 6) :y2 drop-distance})]]]
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

(defn line-points [name]
  (let [all-lines (get-in @state [:my-lines])
        one-line (first (filter #(= (:name %) name) all-lines))
        ;_ (log "LINE: " one-line)
        its-points (:points one-line)
        ;_ (log "POINTS: " its-points)
        ]
    its-points))

;;
;; Any x may have two positions, one on either side, or none. These two positions will be useful to the drop-down
;; y-line that comes up as the user moves the mouse over the graph.
;; In the reduce implementation we only know the previous one when we have gone past it, hence we need to keep the
;; prior in the accumulator.
;; Because of the use-case, when we are exactly on it we repeat it. I'm thinking the two values will have the greatest
;; or least used. This obviates the question of there being any preference for before or after. Also when user is at
;; the first or last point there will still be a result.
;;
(defn enclosed-by [x line-name]
  (let [points (line-points line-name)
        ;_ (u/log "positions to reduce over: " positions)
        res (reduce (fn [acc ele] (if (empty? (:res acc))
                                    (let [cur-x (first ele)]
                                      (if (= cur-x x)
                                        {:res [ele ele]}
                                        (if (> cur-x x)
                                          {:res [(:prev acc)] :prev ele} ;use the prior element
                                          {:res [] :prev ele} ;only update prior element
                                          )
                                        )
                                      )
                                    (let [result-so-far (:res acc)]
                                      (if (= 1 (count result-so-far))
                                        {:res (conj result-so-far (:prev acc))}
                                        acc)
                                      )
                                    ))
                    []
                    points)
        ]
    (let [result (:res res)]
      (if (empty? result)
        nil
        (if (= 1 (count result))
          (let [last-ele (last points)]
            {:name line-name :pair (conj result last-ele)})
          {:name line-name :pair result})))))

(defn show-labels-moment [x]
  (let [names (get-names)
        _ (log "names: " names)
        surrounding-at (partial enclosed-by x)
        ;trans-point-fn (:whole-point translator)
        many-enclosed-by-res (remove nil? (map surrounding-at names))
        _ (log "enclosed result: " many-enclosed-by-res)
        results (for [[left-of right-of] (map :pair many-enclosed-by-res)
                           name (map :name many-enclosed-by-res)
                           :let [_ (log name " left: " left-of)
                                 _ (log name " right: " right-of)
                                 _ (log name " x: " x)
                                 y-intersect (bisect-vertical-between left-of right-of x)]]
                       {:name name :y-intersect y-intersect})]
    (vec results)))

;;
;; When in sticky time we want mouse movement to be ignored.
;; Thus if user drags to a place and leaves it there for a second, he can then move the cursor out of the way
;; A further refinement would be for the moving away to make it 'stuck'
;; (and clicking would also have to have this effect)
;;
(defn tick []
  (let [in-sticky-time? (fn [past-time current-time]
                          (if (or (nil? current-time) (nil? past-time))
                            false
                            (let [diff (- (seconds current-time) (seconds past-time))]
                              (< 1 diff 4))))]
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
            (swap! state assoc-in [:labels] (show-labels-moment x))
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
        [(tick-lines-over hover-pos) (get-in @state [:labels])]
        )
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
        tick-fn (tick)
        _ (js/setInterval #(tick-fn) 500)
        ]
    (reagent/render-component
      [trending-app args]
      (.-body js/document))
    (go
      (let [exit (<! proc)]
        (prn :exit! exit)))))