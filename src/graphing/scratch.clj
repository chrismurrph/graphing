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
        :let [_ (println line)
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

(defn -main
  [& args]
  (println (all-points-component input))
  )
