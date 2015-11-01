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

(defn -main
  [& args]
  (println "rm-outer-from: " rm-outer-from)
  (println (into [] (apply concat rm-outer-from)))
  )
