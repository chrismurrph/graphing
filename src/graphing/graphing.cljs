(ns graphing.graphing
  (:require [reagent.core :as reagent]
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [graphing.known-data-model :refer [gray black white very-light-blue]]
            [graphing.utils :refer [log distance bisect-vertical-between]]
            [goog.string :as gstring]
            [goog.string.format]
            [graphing.utils :as u])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [cljs.core.match.macros :refer [match]]))

(def ratom reagent/atom)

(defn- rgb-map-to-str [{r :r g :g b :b}]
  (str "rgb(" r "," g "," b ")"))

(defn- format-as-str [dec-pl val units]
  (gstring/format (str "%." dec-pl "f" units) val))

(defn- now-time [] (js/Date.))
(defn- seconds [js-date] (/ (.getTime js-date) 1000))

;;
;; In this namespace a label might be "Tube 1, Oxygen" or "Plastics Eff Ratio" or "BHP Billiton P/E ratio". It is what
;; represents the line that is being graphed. Seen by the human user and used as :line-id. Just a String.
;; Note that labels is made up of things that look like: {:name "Methane", :proportional-y 132.46547214801495, :proportional-val 0.09617788534898025}
;; Whereas :current-label will be eg: {:name "Carbon Monoxide", :tstamp 1448876445368}
;;
(def default-state {:my-lines {} :hover-pos nil :last-mouse-moment nil :labels-visible? false :in-sticky-time? false :labels [] :current-label nil})
(def state (ratom default-state))
(def default-init-state {:translator nil})
(def init-state (atom default-init-state))

(defn- find-line [name]
  (get (:my-lines @state) name))

(def line-defaults
  {:stroke (rgb-map-to-str black)
   :stroke-width 1})

(defn- plum-line [height visible x-position]
  (let [currently-sticky (get-in @state [:in-sticky-time?])
        res (when visible [:line
                           (merge line-defaults
                                  {:x1 x-position :y1 0
                                   :x2 x-position :y2 height
                                   :stroke-width (if currently-sticky 2 1)})])]
    res))

(defn- hidden? [line-id]
  (let [current (:name (:current-label @state))]
    (not= current line-id)))

(defn- text-component [x y-intersect colour-str txt-with-units line-id]
  [:text {:opacity (if (hidden? line-id) 0.0 1.0) :x (+ x 10) :y (+ (:proportional-y y-intersect) 4) :font-size "0.8em" :stroke colour-str}
   (format-as-str (or (:dec-places y-intersect) 2) (:proportional-val y-intersect) txt-with-units)]
  )

(defn- insert-texts [x drop-infos]
  (for [drop-info drop-infos
        :let [line-doing (-> drop-info :name find-line)
              colour-str (-> line-doing :colour rgb-map-to-str)
              units-str (:units line-doing)
              line-id (:name line-doing)
              y-intersect drop-info]]
    ^{:key y-intersect} [text-component x y-intersect colour-str units-str line-id]))

;;
;; Using another :g means this is on a different layer so the text that is put on top of this rect does not have its
;; opacity affected.
;;
(defn- opaque-rect [x y line-id]
  (let [height 16
        half-height (/ height 2)
        width 45 ;; later we might use how many digits there are
        indent 8
        width-after-indent (- width 4)]
    [:g [:rect {:x (+ indent x) :y (- y half-height) :width width-after-indent :height height :opacity (if (hidden? line-id) 0.0 1.0) :fill (rgb-map-to-str white) :rx 5 :ry 5}]]))

(defn- backing-rects [x drop-infos]
  (for [drop-info drop-infos
        :let [y (:proportional-y drop-info)
              line-id (:name drop-info)]]
    ^{:key y} [opaque-rect x y line-id])
  )

;;
;; Many lines coming out from the plum line, then with each the backing rect and then the text
;;
(defn- tick-lines [x visible drop-infos]
  ;(log "info: " drop-infos)
  (when visible (into [:g (backing-rects x drop-infos) (doall (insert-texts x drop-infos))]
                      (for [drop-info drop-infos
                            :let [line-doing (-> drop-info :name find-line)
                                  colour-str (-> line-doing :colour rgb-map-to-str)
                                  ;_ (log (:name drop-info) " going to be " colour-str)
                                  drop-distance (:proportional-y drop-info)
                                  res [:line
                                       (merge line-defaults
                                              {:x1 x :y1 drop-distance
                                               :x2 (+ x 6) :y2 drop-distance
                                               :stroke colour-str
                                               :opacity (if (hidden? (:name line-doing)) 0.0 1.0)})]]]
                        res))))

;;
;; Need to supply visible and x-position to display it
;;
(defn- plum-line-at [height] (partial plum-line height))

;;
;; At the time that the plum-line is made visible, many of these at the same x will also be
;; made visible
;;
(defn- tick-lines-over [x] (partial tick-lines x))

(def point-defaults
  {;:stroke (rgb-map-to-str black)
   ;:stroke-width 2
   :r 2})

;;
;; Creates a point as a component
;;
(defn- point [rgb-map x y]
  ;(log rgb-map)
  [:circle
   (merge point-defaults
          {:cx x
           :cy y
           :fill (rgb-map-to-str rgb-map)})])

; Now it is a map where the key is a line name
;(defn- my-lines-size [] (count (:my-lines @state)))

(defn- controller [inchan]
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

                  ;[{:type "mouseup" :x x :y y}]
                  ;(let [current-line (dec (my-lines-size))]
                  ;  (log "Already colour of current line at " current-line " is " (get-in @state [:my-lines current-line :colour]))
                  ;  (swap! state update-in [:my-lines current-line :points] (fn [points-at-n] (vec (conj points-at-n [x y]))))
                  ;  ;(u/log "When mouse up time is: " when-last-moved)
                  ;  (recur x y old-x old-y))

                  [_]
                  (do
                    (recur cur-x cur-y old-x old-y)))))

(defn- event-handler-fn [comms component e]
  (let [bounds (. (reagent/dom-node component) getBoundingClientRect)
        y (- (.-clientY e) (.-top bounds))
        x (- (.-clientX e) (.-left bounds))]
    (put! comms {:type (.-type e) :x x :y y})
    nil))

(defn- point-component [rgb-map [x y _]]
  ^{:key [rgb-map x y]} [point rgb-map x y])

;;
;; The list comprehension changes hash-map into vector. (Surprised me).
;;
;;
(defn- points-from-lines [my-lines]
  ;(log "ALL: " my-lines)
  (let [translate-point (-> @init-state :translator :point)]
    (for [line-vec my-lines
          :let [;_ (log "LINE: " line-vec)
                line-val (second line-vec)
                colour (:colour line-val)
                ;_ (log "Colour of " (:name line-val) " is " colour)
                component-fn (partial point-component colour)
                points (:points line-val)]
          point points
          :let [component (component-fn (translate-point point))]]
      component)))

(defn- all-points-component [my-lines]
  (into [:g {:key my-lines}]
        ;(map #(point-component nil %) (mapcat identity my-lines))
        (points-from-lines my-lines)
        ))

;(defn hover-visible? [last-mouse-moment now-moment]
;  (if (nil? last-mouse-moment)
;    false
;    (still-interested? last-mouse-moment now-moment)))

(defn- get-names []
  (keys (get-in @state [:my-lines])))

(defn- line-points [name]
  (let [all-lines (vals (get-in @state [:my-lines]))
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
(defn- enclosed-by [x line-name]
  (let [points (line-points line-name)
        translate-horizontally (-> @init-state :translator :horiz)
        ;_ (u/log "positions to reduce over: " positions)
        res (reduce (fn [acc ele] (if (empty? (:res acc))
                                    (let [cur-x (translate-horizontally (key ele))]
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
      ;(log "RES: " result)
      (if (nil? (first result)) ;when are before first element
        nil
        (if (empty? result)
          nil
          (if (= 1 (count result))
            (let [last-ele (last points)]
              {:name line-name :pair (conj result last-ele)})
            {:name line-name :pair result}))))))

(defn- show-labels-moment [x]
  (let [names (get-names)
        ;_ (log "names: " names)
        surrounding-at (partial enclosed-by x)
        many-enclosed-by-res (remove nil? (map surrounding-at names))
        ;_ (log "enclosed result: " many-enclosed-by-res)
        translate-point (-> @init-state :translator :point)
        results (for [enclosed-by-res many-enclosed-by-res
                           :let [{name :name pair :pair} enclosed-by-res
                                 left-of (first pair)
                                 right-of (second pair)
                                 ;_ (log name " left, right, x " left-of " " right-of " " x)
                                 y-intersect (bisect-vertical-between (translate-point left-of) (translate-point right-of) x)]]
                       (into {:name name} y-intersect)
                       )]
    (vec results)))

;;
;; When in sticky time we want mouse movement to be ignored.
;; Thus if user drags to a place and leaves it there for a second, he can then move the cursor out of the way
;; A further refinement would be for the moving away to make it 'stuck'
;; (and clicking would also have to have this effect)
;;
(defn- tick []
  (let [in-sticky-time? (fn [past-time current-time]
                          (if (or (nil? current-time) (nil? past-time))
                            false
                            (let [diff (- (seconds current-time) (seconds past-time))]
                              (< 1 diff 4))))]
  (fn []
    (let [tick-moment (now-time)
          last-time-moved (:last-mouse-moment @state)
          currently-sticky (get-in @state [:in-sticky-time?])
          now-sticking (in-sticky-time? last-time-moved tick-moment)
          x (get-in @state [:hover-pos])
          labels-already-showing? (get-in @state [:labels-visible?])]
      (when labels-already-showing?
        (let [old-tstamp (get-in @state [:current-label :tstamp])
              now-is (.getTime tick-moment)
              diff (- now-is old-tstamp)]
          (when (> diff 2000)
            ;(log "Now it is: " now-is ", whereas timestamp done at: " old-tstamp ", diff: " diff)
            (let [current-label (get-in @state [:current-label :name])
                  nxt-current-label (u/next-in-vec current-label (map :name (get-in @state [:labels])))
                  ;_ (log "Nxt label is " nxt-current-label)
                  ]
              (swap! state assoc-in [:current-label] {:name nxt-current-label :tstamp (.now js/Date)}))
            )
          ))
      (if (not currently-sticky)
        (when now-sticking
          (when (not labels-already-showing?)
            (let [labels-info (show-labels-moment x)
                  first-name (:name (first labels-info))
                  _ (log "First vis: " first-name)]
              (swap! state assoc-in [:labels] labels-info)
              (swap! state assoc-in [:current-label] {:name first-name :tstamp (.now js/Date)}))
            (swap! state assoc-in [:labels-visible?] true)
            (swap! state assoc-in [:in-sticky-time?] true)))
        (when (not now-sticking)
          (swap! state assoc-in [:in-sticky-time?] false)))))))

(def tick-timer
  (let [tick-fn (tick)]
  (go-loop []
           (<! (timeout 500))
           (tick-fn)
           (recur))))

;;
;; Public API
;;
(defn add-line [options-map]
  "All keys in options-map param are: :name :units :colour :dec-places. :name is mandatory and a line with it must not already exist"
  (let [{:keys [name units colour dec-places],
         :or {units "" colour black dec-places 2}} options-map]
    (assert name "Every line must have a name")
    (assert (not (clojure.string/blank? name)) "Line name s/not be blank")
    (assert (nil? (find-line name)) (str "Already have a line called: " name))
    (assert (nil? (get :points options-map)) (str "Points have to be added later (i.e. not now!) for: " name))
    (let [new-line (into {:points (sorted-map)} options-map)]
      (swap! state update-in [:my-lines]
             (fn [existing-lines]
               (conj existing-lines (hash-map name new-line)))))))

;;
;; Public API
;;
(defn remove-all-lines []
  "This will start off with a blank graph - no lines, no points"
  (reset! state default-state))

;;
;; Point that is added here should be in co-ordinate system of the staging area. Has to be in vector format:
;; [x y val]. Intended to be used by the staging area only. The ys come from the top, which users would not expect!
;; Translation needs to be done to whatever is the interval canvas size here.
;; As an obvious aside this means that whenever the canvas size here is changed we should apply the translation
;; again. This will happen when the axes encroach in from the left.
;; Because this is part of the public API (for staging area anyway) the translator is kept in an atom.
;;
(defn add-point [point-map]
  (let [name (:name point-map)
        [x y val] (:point point-map)
        ;translate-horizontally (-> @init-state :translator :horiz)
        ;translate-vertically (-> @init-state :translator :vert)
        ]
    (assert (not (clojure.string/blank? name)) "Point trying to add must belong to a line, so need to supply a name")
    (assert (integer? y) (str "y must be an integer, got: <" y "> from: " point-map))
    (assert (number? val) "val must be a number")
    (assert (integer? x) (str "x must be an integer, got: <" x "> from: " point-map))
    (let [found-line (find-line name)]
      (assert found-line (str "Line must already exist for the point to be added to it. Could not find line with name: " name))
      (swap! state update-in [:my-lines name :points]
             (fn [existing-points]
               ; This pair we are conj-ing in will become a MapEntry in the sorted-map that is `:points`
               (conj existing-points [x [y val]]))))))

(defn- main-component [options-map]
  (let [{:keys [handler-fn height width]} options-map
        {:keys [my-lines hover-pos labels-visible?]} @state]
    [:div
     [:svg {:height height :width width
            :on-mouse-up handler-fn :on-mouse-down handler-fn :on-mouse-move handler-fn
            :style {:border "thin solid black"}}
      [all-points-component my-lines]
      [(plum-line-at height) (not (nil? hover-pos)) hover-pos]
      [(tick-lines-over hover-pos) labels-visible? (get-in @state [:labels])]
      ]
     [:input {:type "button" :value "Methane"
              :on-click #(log "Methane")}]
     [:input {:type "button" :value "Oxygen"
              :on-click #(log "Oxygen")}]
     [:input {:type "button" :value "line called"
              :on-click #(log (find-line "Oxygen"))}]
     ]))

;;
;; args is given everything from state, completely unnecessary
;;
(defn- trending-app [options-map]
  (let [component (reagent/current-component)
        handler-fn (partial event-handler-fn (:comms options-map) component)
        args (into {:handler-fn handler-fn} (dissoc options-map :comms))]
    [main-component args]))

(defn- staging-translators [min-x min-y max-x max-y graph-width graph-height]
  (let [horiz-trans-fn (fn [val] (u/scale {:min min-x :max max-x} {:min 0 :max graph-width} val))
        vert-trans-fn (fn [val] (u/scale {:min min-y :max max-y} {:min 0 :max graph-height} val))
        trans-point-fn (fn [[x [y val]]] [(horiz-trans-fn x) (vert-trans-fn y) val])
        ]
    {:horiz horiz-trans-fn :vert vert-trans-fn :point trans-point-fn}))

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
        staging (:staging options-map)
        graph-width (:width options-map)
        _ (assert graph-width ":width needs to be supplied at init")
        graph-height (:height options-map)
        _ (assert graph-height ":height needs to be supplied at init")
        translators (staging-translators (or (:min-x staging) 0) (or (:min-y staging) 0) (or (:max-x staging) 999) (or (:max-y staging) 999) graph-width graph-height)
        ]
    (reset! state default-state)
    ;(log "TRANS: " translators)
    (swap! init-state assoc-in [:translator] translators)
    (reagent/render-component
      [trending-app args]
      (.-body js/document))
    (go
      (let [exit (<! proc)]
        (prn :exit! exit)))))