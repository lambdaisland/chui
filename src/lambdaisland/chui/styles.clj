(ns lambdaisland.chui.styles
  (:require [garden.core :as garden]
            [garden.stylesheet :as stylesheet]
            [garden.selectors :as selectors]))

(def washed-red "#FFDFDF")
(def washed-green "#E8FDF5")

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

   [:.run.fail [:h1 {:background-color washed-red}]]
   [:.run.error [:h1 {:background-color washed-red}]]
   [:.run.pass [:h1 {:background-color washed-green}]]
   [:.ns.error {:background-color washed-red}]
   [:.ns.fail {:background-color washed-red}]
   [:.ns.pass {:background-color washed-green}]])

(selectors/defselector input)

(def search-input (partial input (selectors/attr= "type" "search")))

(def style2
  [[:* {:box-sizing "border-box"}]
   [:html {:color "#333"
           :font-family "sans-serif"
           :height "100vh"}]
   [:body {:margin 0
           :height "100%"}]
   [:#app {:height "100%"}
    [:> [:div {:height "100%"
               :display :grid
               :grid-template-rows "auto 1fr"
               :grid-gap ".3rem"
               :padding ".3rem"}]]]
   [:header {:background-color "#6b6bff"
             :color "#7cdfff"
             :padding ".3rem"
             :border-radius ".1rem"
             :display :flex
             :justify-content :space-between}]
   [:.interface-controls {:display :flex}]
   [:ul {:padding ".2rem"
         :list-style :none
         :text-decoration :none
         :line-height 1.5}]
   [:li [:a {:text-decoration :none}]]
   [:main
    {:display :flex
     :width "100%"
     :overflow-x "auto"
     :scroll-snap-type "x mandatory"
     :scrollbar-width :none
     :background-color :initial}
    [:&.cols-2 [:>section {:width "calc(100vw / 3)"}]]
    [:&.cols-3
     [:>section {:width "25vw"}
      [:&:last-child {:width "50vw"}]]]
    [:&.cols-4
     [:>section {:width "20vw"}
      [:&:last-child {:width "40vw"}]]]
    [:>section {:flex-shrink 0
                :scroll-snap-align :center
                :scrollbar-width :none
                :display :flex
                :flex-direction :column
                :padding ".5rem"
                :overflow "scroll"}
     [:&:hover {:background-color :snow}]]]
   [:.namespaces {:background-color :inherit}]
   [:.fieldset {:border "1px solid black"
                :margin-top ".3rem"
                :margin-bottom ".3rem"}]
   [(search-input)
    {:padding ".5rem"
     :border "none"
     :width "100%"
     :font-size "1.1rem"
     :line-height 1.5}]
   [(search-input "::placeholder") {:color :gray}]
   [:.selection-target
    [:&:hover {:background-color "#ff8"}]
    [:&.selected {:background-color "#cfc"}]]
   [:.history { :background-color :inherit}

    [:.run {:margin-bottom "2rem"}]]
   [:.section-header {:font-size "1.1rem"
                      :font-weight "bold"
                      :width "100%"
                      :border-bottom "1px solid #000"
                      :margin 0}]
   [:.test-info { :background-color :initial}]
   [:.namespaces [:+ul {:padding-left "1.5rem"
                        :line-height "1.7rem"}]]
   [:.name {:color :white
            :text-decoration :none
            :padding-right ".3rem"}]
   [:.toggle {:position :absolute
              :left "-100vw"}]
   [:.namespace-selector {:display :flex
                          :flex-direction :column
                          :margin-top ".5rem"
                          :line-height 1.125}]
   [:.active {:font-weight :bold}]
   [:.search-bar {:display :grid
                  :background-color :whitesmoke
                  :box-shadow "1px 1px 5px whitesmoke"
                  :grid-template-columns "1fr 22%"
                  :grid-auto-flow :column
                  :border "1px solid whitesmoke"
                  :position :sticky
                  :top 0}]
   [:.button {:font-variant-caps :all-small-caps
              :font-weight :bold
              :background-color :inherit
              :border :none
              :font-size "1.1rem"}
    [:&:hover {:color :white
               :cursor :pointer}]]
   [:.run-tests {:color :silver}
    [:&:hover :&:active {:background-color :lightgreen}]]
   [:.stop-tests {:color :coral}
    [:&:hover {:background-color :lightcoral}]]
   [:.namespace-links
    {:font-size "1rem"
     :display :flex
     :justify-content :space-between
     :border-radius "2px"
     :margin-bottom ".25rem"}
    [:* {}
     [:&:selected {:background-color :fuchsia}]]
    [:input {:display :none}]
    [:label {:display :flex
             :justify-content :space-between
             :width "100%"
             :padding ".50rem .5rem"}]
    [:aside {:font-style :italic
             :color :darkgray}]
    [:.skip {:color :darkgray}]]
   [:.result-viz
    [:.ns {:border "1px solid #113"
           :border-radius "4px"
           :margin-right "3px"
           :overflow-wrap :break-word
           :line-height "2rem"}]
    [:.var {:border-right "1px solid #113"}
     [:&:first-child {:border-radius "4px 0 0 4p"}]
     [:&:last-child {:border-style :none}]]
    [:.assertion
     {:font-variant-caps :all-small-caps
      :font-weight :bold
      :color :darkgray
      :padding "0 4px"}]
    [:.selected-ns
     [:.pass {:color :white :background-color :green}]
     [:.fail {:color :white :background-color :red}]
     [:.error {:color :white :background-color :red}]]
    [:.pass {:background-color washed-green}]
    [:.fail {:background-color washed-red}]
    [:.error {:background-color washed-red}]]
   [:.ns-run
    {:margin-bottom "2rem"}
    [:.filename {:float :right
                 :color :darkgray}]]
   [:.ns-run-var
    [:.result-viz-var

     [:.assertion
      {:font-variant-caps :all-small-caps
       :font-weight :bold
       :color :darkgray
       :padding "0 4px"
       }]
     [:.pass {:color :white :background-color :green}]
     [:.fail {:color :white :background-color :red}]
     [:.error {:color :white :background-color :red}]]]])

(defmacro inline []
  (garden/css {:pretty-print? false} style2))
