(ns graphing.scratch)

(def zero-points '([0 0] [0 0] [0 0]))
(def one-points [[1 1] [1 1] [1 1]])

(def global-state (atom {:my-lines []}))

(defn stuff-coll-in [points n]
  (swap! global-state assoc-in [:my-lines n] points))

(defn stuff-one-in [n point]
  (swap! global-state update-in [:my-lines n] (fn [coll-at-n] (conj coll-at-n point))))

(defn -main
  [& args]
  ;(println "Source points: " some-points)
  (stuff-coll-in zero-points 0)
  (stuff-coll-in one-points 1)
  (stuff-one-in 0 [10 10])
  (println "From inside: " (get-in @global-state [:my-lines]))
  )
