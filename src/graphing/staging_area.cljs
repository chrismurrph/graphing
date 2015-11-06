(ns graphing.staging-area
  (:require [graphing.graph-lines-db :refer [light-blue green pink]]
            ))

;;
;; [x y real-value]
;; Note that y is greater as lower but real value will be smaller as lower
;;
(def first-line {:name "First line" :colour light-blue :dec-places 3 :units "" :points [[10 10 0.511] [20 20 0.411] [30 30 0.311] [40 40 0.211] [50 50 0.111]]})

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


