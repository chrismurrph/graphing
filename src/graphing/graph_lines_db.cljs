(ns ^:figwheel-always graphing.graph-lines-db
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
;; All the lines that get graphed. Has nothing to do with Reagent, so use a normal atom.
;; :x and :y are both always in the range 0-999. Scaling to the actual graph enclosure is done at drawing time
;; i.e. completely dynamically.
;; Note usual computer/graphical rules apply, so x starts at 0 and goes across the top, and y then goes down from
;; the top.
;; Note that the x value is always increasing, meaning older come before newer, because x is time.
;; TODO - change names here and at mouse-at to be keywords
;;
(def lines (atom [
                  {:colour pink
                   :name "Methane"
                   :positions [{:x 20 :y 320 :val 0} {:x 51 :y 321 :val 0} {:x 82 :y 422 :val 0} {:x 104 :y 523 :val 0}
                               {:x 115 :y 426 :val 0} {:x 227 :y 428 :val 0} {:x 320 :y 532 :val 0} {:x 344 :y 333 :val 0}
                               {:x 435 :y 435 :val 0} {:x 440 :y 338 :val 0} {:x 541 :y 243 :val 0} {:x 644 :y 446 :val 0}]}
                  {:colour green
                   :name "Oxygen"
                   :positions [{:x 40 :y 350 :val 0} {:x 141 :y 351 :val 0} {:x 242 :y 352 :val 0} {:x 344 :y 353 :val 0}
                               {:x 445 :y 454 :val 0} {:x 546 :y 459 :val 0} {:x 548 :y 460 :val 0} {:x 650 :y 461 :val 0}
                               {:x 651 :y 563 :val 0} {:x 653 :y 565 :val 0} {:x 755 :y 565 :val 0} {:x 857 :y 568 :val 0} {:x 960 :y 672 :val 0}]}]))

;;
;; As mousemove and has been there a small period of time then this is written to. Is then picked up by the draw function.
;;
(def mouse-at-x (atom 350))
(def mouse-at-ys (atom [{:name "Hydrogen" :value 0.07 :y 300} {:name "Boron" :value 0.17 :y 310}]))

(defn get-external-line [name]
  (first (filter #(= name (-> % :name)) @lines)))

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

;;
;; Any x may have two positions, one on either side, or none. These two positions will be useful to the drop-down
;; y-line that comes up as the user moves the mouse over the graph.
;; In the reduce implementation we only know the previous one when we have gone past it, hence we need to keep the
;; prior in the accumulator.
;; Because of the use-case, when we are exactly on it we repeat it. I'm thinking the two values will have the greatest
;; or least used. This obviates the question of there being any preference for before or after. Also when user is at
;; the first or last point there will still be a result.
;; Because x comes from the screen and we only ever translate bus -> scr, and we only ever actually see business
;; values (translation is done as values are rendered), then as we look thru the x values of elements from the
;; external (i.e. business) line we must translate them to what was/is on the screen, just for the benefit of the
;; incoming x.
;;
(defn enclosed-by [translate-x x line-name]
  (let [line (get-external-line line-name)
        positions (:positions line)
        ;_ (u/log "positions to reduce over: " positions)
        res (reduce (fn [acc ele] (if (empty? (:res acc))
                                    (let [cur-x (translate-x (:x ele))]
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
                    positions)
        ]
    (let [result (:res res)]
      (if (empty? result)
        nil
        (if (= 1 (count result))
          (let [last-ele (last positions)]
            (conj result last-ele))
          result)))))

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