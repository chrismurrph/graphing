(ns graphing.core
  (:require [reagent.core :as reagent]
            [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]]
            [cljs.core.match]
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

(def point-defaults
  {:stroke "black"
   :stroke-width 2
   :fill "blue"
   :r 5})

;;
;; Creates a point as a component
;;
(defn point [x y]
  [:circle
   (merge point-defaults
          {:cx x
           :cy y})])

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

(defn new-controller [inchan state-ref]
  (go-loop [cur-x nil cur-y nil mouse-state :up]
           (match [(<! inchan) mouse-state]

                  [({:type "mousemove" :x x :y y} :as e) :up]
                  (do
                    ;(u/log "Moved to " x " " y)
                    (recur x y :up))

                  [({:type "mouseup" :x x :y y} :as e) :up]
                  (do
                    (swap! state-ref (fn [{:keys [current-path paths my-points] :as state}]
                                       (assoc state :my-points (conj my-points [x y]))))
                    (u/log (:my-points @state-ref))
                    (recur x y :up))

                  [s e] (recur cur-x cur-y mouse-state))))

(defn old-path-component [its-path fill-color]
   (let [xys (map (fn [{:keys [x y]}] (str x " " y)) its-path)
         points (apply str (interpose ", " xys))
         idx (gen-key)
         _ (u/log idx " " (count points))
         ]
     [:polyline {:key idx :points points :stroke fill-color :fill "none"}]))

(defn new-path-component [its-path fill-color]
  (into [:polyline]
        (for [xys (map (fn [{:keys [x y]}] (str x " " y)) its-path)
              points (apply str (interpose ", " xys))
              idx (gen-key)]
          {:key idx :points points :stroke fill-color :fill "none"})))

(defn event-handler-fn [comms component e]
  (let [bounds (. (reagent/dom-node component) getBoundingClientRect)
        y (- (.-clientY e) (.-top bounds))
        x (- (.-clientX e) (.-left bounds))]
     (put! comms {:type (.-type e) :x x :y y})
     nil))

(defn my-points-component []
  [:g
   [point 10 10]
   [point 20 20]])

(defn trending-app [{:keys [state-ref comms] :as props}]
  (let [{:keys [paths my-points current-path]} @state-ref
        component (reagent/current-component)
        handler-fn (partial event-handler-fn comms component)
        ]
    [:svg {:height 480 :width 640 :on-mouse-up handler-fn :on-mouse-down handler-fn :on-mouse-move handler-fn :style {:border "thin solid black"}}
     (cons [new-path-component current-path "red"]
      (map #(vector new-path-component % "black") paths))
      ;[my-points-component]
      ]))
      
(defn mount-root []
  (let [paths-ratom (ratom {:paths [] :my-points []})
        ch (chan)
        proc (old-controller ch paths-ratom)]
    (reagent/render-component
       [trending-app {:state-ref paths-ratom :comms ch}]
       (.-body js/document))
    (go
      (let [exit (<! proc)]
        (prn :exit! exit)))))      

(defn ^:export run []
    (mount-root))