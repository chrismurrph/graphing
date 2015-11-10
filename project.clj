(defproject graphing "0.1.0-SNAPSHOT"
  :description "dev project to get trending going in svg with reagent"
  :url "http://localhost:3449/"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145"]
		 [reagent "0.5.1"]
		 [org.clojure/core.async "0.2.371"]
		 [org.clojure/core.match "0.2.1"]
		 [figwheel "0.4.1"]
     ;[com.andrewmcveigh/cljs-time "0.3.14"]
                 ]

  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-figwheel "0.4.1"]]

  :source-paths ["src"]
  
  :clean-targets ^{:protect false} ["resources/public/js" "target" 
                                    "resources/public/css"]

  :cljsbuild { 
    :builds [{:id "graphing"
              :source-paths ["src"]
              :figwheel {:on-jsload "graphing.core/mount-root"}
              :compiler {
                :main graphing.core
                :output-to "resources/public/js/graphing.js"
                :output-dir "resources/public/js/out"
                :asset-path "js/out"
                :optimizations :none}}]                
                }

  :main ^:skip-aot graphing.scratch)
