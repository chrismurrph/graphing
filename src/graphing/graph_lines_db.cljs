(ns graphing.graph-lines-db
  (:require [graphing.utils :as u]))

(def black {:r 0 :g 0 :b 0})
(def blue {:r 0 :g 51 :b 102})
(def light-blue {:r 0 :g 204 :b 255})
(def pink {:r 255 :g 0 :b 255})
(def brown {:r 102 :g 51 :b 0})
(def green {:r 0 :g 102 :b 0})
(def red {:r 255 :g 0 :b 0})
(def gray {:r 64 :g 64 :b 64})

;;
;; Having colour as {:r :g :b} everywhere makes sense, so no need for this
;;
;(defn vector-form [colour-map]
;  (let [{r :r g :g b :b} colour-map]
;    [r g b]))

(def local-mouse-pos (atom [0 0]))
(def last-time-mouse-moved (atom 0))

;;
;; As mousemove and has been there a small period of time then this is written to. Is then picked up by the draw function.
;;
(def mouse-at-x (atom 350))
(def mouse-at-ys (atom [{:name "Hydrogen" :value 0.07 :y 300} {:name "Boron" :value 0.17 :y 310}]))

;(defn get-external-line [name]
;  (first (filter #(= name (-> % :name)) @lines)))

;;
;; Client will know the width and height of the area it needs to put dots onto. These will change and every time
;; they do a new scale function can be requested here. Note that the world of this namespace is 0 - 999 inclusive.
;; If height changes a new height partial function will be requested.
;; At any time for drawing a dot on the actual canvas there will be two partial functions originally gained from
;; here. `scale-height` for the y value and `scale-width` for the x value.
;;
(defn scale-fn [width-or-height]
  (partial u/scale {:min 0 :max 999} {:min 0 :max width-or-height}))

;;
;; We are always going to get y duplicates so x are interesting. Two events s/not happen at the same time.
;; There is no purpose to this function yet, just done for the sake of interest.
;; First up we convert from vector to a vectors of hash-maps where key is the scaled x value. Then we can group-by this scaled
;; x value. Then filter when more than 2 and return a vector of scaled x values. The size of this vector over the
;; number of positions in the line tells how much squashing is going on.
;;
(defn find-x-duplicates [scaling-fn line-name]
  (let [by-scaled-x (map #(vector (scaling-fn (:x %)) %) (:positions (get-external-line line-name)))
        grouping-fn (fn [[k v]] k)
        grouped (group-by grouping-fn by-scaled-x)
        filter-fn (fn [[k v]] (> (count v) 1))
        filtered (filter filter-fn grouped)
        res (map first filtered)
        ;_ (u/log by-scaled-x)
        ;_ (u/log grouped)
        ;_ (u/log filtered)
        _ (u/log res)
        ]
    res))

(defn get-names []
  (map :name @lines))

(defn remover [idx coll]
  (u/vec-remove coll idx))

(defn random-position-removal []
  (let [line-idx (rand-int (count @lines))
        so-line (nth @lines line-idx)
        so-positions (:positions so-line)
        positions-count (count so-positions)]
    (when (pos? positions-count)
      (let [pos-idx (rand-int positions-count)
            remover-at-idx (partial remover pos-idx)]
        (swap! lines update-in [line-idx :positions] remover-at-idx)))))