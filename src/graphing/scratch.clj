(ns graphing.scratch)

(def zero-points '([0 0] [0 0] [0 0]))
(def one-points [[1 1] [1 1] [1 1]])

(def global-state (atom {:my-lines []}))

(def rm-outer-from ['([314.0028409957886 149.00284099578857] [175.00284099578857 139.00284099578857])])

(defn stuff-coll-in [points n]
  (swap! global-state assoc-in [:my-lines n] points))

(defn stuff-one-in [n point]
  (swap! global-state update-in [:my-lines n] (fn [coll-at-n] (conj coll-at-n point))))

(defn stuffing-in []
  (stuff-coll-in zero-points 0)
  (stuff-coll-in one-points 1)
  (stuff-one-in 0 [10 10])
  (println "From inside: " (get-in @global-state [:my-lines])))

(defn next-thing []
  (println "rm-outer-from: " rm-outer-from)
  (println (into [] (apply concat rm-outer-from))))

(def input [{:name "First line", :colour "blue", :points [[10 10] [20 20] [30 30] [40 40] [50 50]]}])

(def uniqkey (atom 0))
(defn gen-key []
  (let [res (swap! uniqkey inc)]
    ;(u/log res)
    res))

(def point-defaults
  {:stroke "black"
   :stroke-width 2
   :r 5})

(defn point [fill [x y]]
  [:circle
   (merge point-defaults
          {:cx x
           :cy y
           :fill fill})])

(defn point-component [fill [x y]]
  ^{:key (gen-key)} (point fill [x y]))

(defn points-from-lines [my-lines]
  (println my-lines)
  (for [line my-lines
        :let [;_ (println line)
              colour (:colour line)
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

(def black {:r 0 :g 0 :b 0})

(defn rgb-map-to-str [{r :r g :g b :b}]
  (str "rgb(" r "," g "," b ")"))

(def line-defaults
  {:stroke (rgb-map-to-str black)
   :stroke-width 1})

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

(def example-map {"Chris" [[0 0]]})

(def some-points (into (sorted-map) [[23 [1 2]] [17 [3 4]]]))

(def last-day-of-months {"Jan" 31 "Feb" 28 "Mar" 31 "Apr" 30 "May" 31 "Jun" 30 "Jul" 31 "Aug" 31 "Sep" 30 "Oct" 31 "Nov" 30 "Dec" 31})
(def months ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"])
(defn month-as-number [month-str] (.indexOf months month-str))

(defn -main
  [& args]
  ;(println (tick-lines 20 [60 70]))
  ;(println (all-points-component input))
  ;(println some-points)
  ;(println (conj some-points [13 [5 6]]))
  ;(let [points (for [point some-points]
  ;               point)]
  ;  (println points))
  (println (month-as-number "Jan"))
  (println (month-as-number "Oct"))
  )
