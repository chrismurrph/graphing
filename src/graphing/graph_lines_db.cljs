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
;; Client will know the width and height of the area it needs to put dots onto. These will change and every time
;; they do a new scale function can be requested here. Note that the world of this namespace is 0 - 999 inclusive.
;; If height changes a new height partial function will be requested.
;; At any time for drawing a dot on the actual canvas there will be two partial functions originally gained from
;; here. `scale-height` for the y value and `scale-width` for the x value.
;;
(defn scale-fn [width-or-height]
  (partial u/scale {:min 0 :max 999} {:min 0 :max width-or-height}))

(defn remover [idx coll]
  (u/vec-remove coll idx))


;;
;; All the lines that get graphed. Has nothing to do with Reagent, so use a normal atom.
;; :x and :y are both always in the range 0-999. Scaling to the actual graph enclosure is done at drawing time
;; i.e. completely dynamically.
;; Note usual computer/graphical rules apply, so x starts at 0 and goes across the top, and y then goes down from
;; the top.
;; Note that the x value is always increasing, meaning older come before newer, because x is time.
;;
(def lines (atom [
                  {:name "First line" :colour light-blue :dec-places 3 :units "" :positions [{:x 10 :y 10 :val 0.511} {:x 20 :y 20 :val 0.411} {:x 30 :y 30 :val 0.311} {:x 40 :y 40 :val 0.211} {:x 50 :y 50 :val 0.111}]}
                  {:colour pink
                   :name "Methane"
                   :units "%"
                   :positions [{:x 20 :y 320 :val 0.01} {:x 51 :y 321 :val 0.02} {:x 82 :y 422 :val 0.05} {:x 104 :y 523 :val 0.07}
                               {:x 115 :y 426 :val 0.1} {:x 227 :y 428 :val 0.12} {:x 320 :y 532 :val 0.14} {:x 344 :y 333 :val 0.16}
                               {:x 435 :y 435 :val 0.19} {:x 440 :y 338 :val 0.2} {:x 541 :y 243 :val 0.22} {:x 644 :y 446 :val 0.23}]}
                  {:colour green
                   :name "Oxygen"
                   :units "%"
                   :positions [{:x 40 :y 350 :val 22} {:x 141 :y 351 :val 21} {:x 242 :y 352 :val 20} {:x 344 :y 353 :val 19}
                               {:x 445 :y 454 :val 18} {:x 546 :y 459 :val 17} {:x 548 :y 460 :val 16} {:x 650 :y 461 :val 15}
                               {:x 651 :y 563 :val 14} {:x 653 :y 565 :val 13} {:x 755 :y 565 :val 12} {:x 857 :y 568 :val 11} {:x 960 :y 672 :val 10}]}]))
