(defproject graphing "0.1.0-SNAPSHOT"
  :description "dev project to get trending going in svg with reagent"
  :url "http://localhost:3449/"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3211"]
		 [reagent "0.5.0"]
		 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
		 [org.clojure/core.match "0.2.1"]
		 [figwheel "0.3.3"]]

  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-figwheel "0.3.3"]]  

  :source-paths ["src"]
  
  :clean-targets ^{:protect false} ["resources/public/js" "target" 
                                    "resources/public/css"]

  :cljsbuild { 
    :builds [{:id "graphing"
              :source-paths ["src"]
              :figwheel {:on-jsload "graphing.core/mount-root"}
              :compiler {
                :main graphing.core
                ;:preamble ["reagent/react.js"]
                :output-to "resources/public/js/graphing.js"
                :output-dir "resources/public/js/out"
                :asset-path "js/out"
                :optimizations :none}}]                
                })
