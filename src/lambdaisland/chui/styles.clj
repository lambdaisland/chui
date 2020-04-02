(ns lambdaisland.chui.styles
  (:require [garden.core :as garden]
            [garden.stylesheet :as stylesheet]))

(def styles
  [[:main
    {:margin "5vh 10vw"
     :font-family "sans-serif"}]

   [:.spinner
    {:display "inline-block"
     :width "1em"
     :height "1em"}
    [:&:after
     {:content "\" \""
      :display "block"
      :width "0.8em"
      :height "0.8em"
      :border-radius "50%"
      :border "6px solid #555"
      :border-color "#555 transparent #555 transparent"
      :animation "spinner 1.2s linear infinite"}]]

   (stylesheet/at-keyframes
    :spinner
    ["0%" {:transform "rotate(0deg)"}]
    ["100%" {:transform "rotate(360deg)"}])

   [:.run.error :section.fail {:background-color "#FFDFDF"}]
   [:.run.pass {:background-color "#E8FDF5"}]
   [:.ns
    [:h2 {:border-bottom "1px solid #666"}]
    [:.filename {:float "right"
                 :color "#777"}]]])

(defmacro inline []
  (garden/css {:pretty-print? false} styles))
