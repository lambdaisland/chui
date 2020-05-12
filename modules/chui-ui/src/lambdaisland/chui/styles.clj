(ns lambdaisland.chui.styles
  (:require [garden.core :as garden]
            [garden.stylesheet :as stylesheet]
            [garden.selectors :as selectors]))

(def fail-color  "orange")
(def error-color "crimson")
(def pass-color  "#29908a")

;; https://github.com/chriskempson/base16-tomorrow-scheme/blob/master/tomorrow.yaml
(def tomorrow
  {:white    "#ffffff"
   :gray1    "#e0e0e0"
   :gray2    "#d6d6d6"
   :gray3    "#8e908c"
   :gray4    "#969896"
   :gray5    "#4d4d4c"
   :gray6    "#282a2e"
   :black    "#1d1f21"
   :red      "#c82829"
   :orange   "#f5871f"
   :yellow   "#eab700"
   :green    "#718c00"
   :turqoise "#3e999f"
   :blue     "#4271ae"
   :purple   "#8959a8"
   :brown    "#a3685a"})

(selectors/defselector input)

(def search-input (partial input (selectors/attr= "type" "search")))

(def puget-color-styles
  [[:code
    [:.class-delimiter {:color (tomorrow :brown)}]
    [:.class-name {:color (tomorrow :brown)}]
    [:.nil {:color (tomorrow :gray5)}]
    [:.boolean {:color (tomorrow :gray5)}]
    [:.number {:color (tomorrow :blue)}]
    [:.character {:color (tomorrow :brown)}]
    [:.string {:color (tomorrow :turqoise)}]
    [:.keyword {:color (tomorrow :blue)}]
    [:.symbol {:color (tomorrow :turqoise)}]
    [:.delimiter {:color (tomorrow :purple)}]
    [:.function-symbol {:color (tomorrow :purple)}]
    [:.tag {:color (tomorrow :brown)}]
    [:.insertion {:color (tomorrow :green)}]
    [:.deletion {:color (tomorrow :red)}]]])

(def style
  [[:body {:overflow :hidden}]
   [:#chui
    [:* {:box-sizing "border-box"}]]
   [:html {:color "#333"
           :font-family "sans-serif"
           :height "100vh"}]
   [:body {:margin 0
           :height "100%"}]
   [:#chui :#chui-container {:height "100%"}
    [:> [:div {:height "100%"
               :display :grid
               :grid-template-rows "auto 1fr"
               :grid-gap ".3rem"}]]]
   [:.top-bar {:background-color (:blue tomorrow)
               :color "#7cdfff"
               :padding ".5rem"
               :display :flex
               :justify-content :space-between}
    [:.button {:padding ".3rem .6rem"
               :background-color :whitesmoke
               :border-radius "2px"}]
    [:.general-toggles
     [:button :label {:margin-right "1rem"}]
     [:input {:margin-right ".5rem"}]]
    [:.name {:color :white
             :text-decoration :none
             :font-size "1.5rem"
             :padding-right ".3rem"}]]
   [:.interface-controls {:display :flex}]
   [:.card {:border "1px solid #eee"
            :box-shadow "1px 1px 5px #eee"}]
   [:.inner-card {:border "1px solid #eee"}]
   [:ul {:padding ".2rem"
         :list-style :none
         :text-decoration :none
         :line-height 1.5}]
   [:li [:a {:text-decoration :none}]]
   [:code {:font-size "1.1rem"}]
   [:main
    {:display :flex
     :width "100%"
     :overflow-x "auto"
     :scroll-snap-type "x mandatory"
     :scrollbar-width :none
     :background-color :initial}
    [:&.cols-2 [:>section {:width "calc(100vw / 3)"}]]
    [:&.cols-3
     [:>section {:flex 1}
      [:&:last-child {:flex 2}]]]
    [:&.cols-4
     [:>section {:width "20vw"}
      [:&:last-child {:width "40vw"}]]]
    [:>section {:flex-shrink 0
                :display :flex
                :flex-direction :column
                :padding ".5rem"
                :overflow :auto}
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
    [:&.selected {:background-color "lightgoldenrodyellow"}]]
   [:.history { :background-color :inherit}]
   [:.section-header {:font-size "1.1rem"
                      :font-weight "bold"
                      :width "100%"
                      :margin 0}]
   [:.test-info {:background-color :initial
                 :padding ".5rem 1rem 1rem"
                 :margin-bottom "1rem"}
    [:.inner-card {:padding ".3rem .5rem"
                   :margin ".5rem 0"}]
    [:.assertion {:position :relative
                  :overflow-y :auto}]
    [:.context :.message {:margin-bottom ".3rem"}]
    [:.pass {:border-right (str "4px solid " pass-color)}]
    [:.fail {:border-right (str "4px solid " fail-color)}]
    [:.error {:border-right (str "4px solid " error-color)}]
    [:aside {:position :absolute
             :top 0
             :right 0
             :font-weight :bold
             :font-variant-caps :all-small-caps
             :padding ".2rem .5rem"}]
    [:h4 {:margin 0
          :font-variant-caps :all-small-caps
          }]
    [:.bottom-link {:width "100%"
                    :display :block
                    :text-align :right
                    :margin-top "1rem"}]
    ]
   [:.namespaces [:+ul {:padding-left "1.5rem"
                        :line-height "1.7rem"}]]
   [:.toggle {:position :absolute
              :left "-100vw"}]
   [:.namespace-selector {:display :flex
                          :flex-direction :column
                          :margin-top ".5rem"
                          :line-height 1.125}]
   [:.active {:font-weight :bold}]
   [:.search-bar {:display :grid
                  :background-color :whitesmoke
                  :grid-template-columns "4fr minmax(26%, 1fr)"
                  :grid-auto-flow :column
                  :position :sticky
                  :top 0}]
   [:.button {:font-variant-caps :all-small-caps
              :font-weight :bold
              :background-color :inherit
              :border :none
              :font-size "1.1rem"}
    [:&:hover {:color :white
               :cursor :pointer}]]
   [:.run-tests {:color :silver
                 :line-height ".9"}
    [:&:hover :&:active {:background-color :lightgreen}]
    [:&:hover:disabled {:background-color :silver}]]
   [:.stop-tests {:color :coral}
    [:&:hover {:background-color :lightcoral}]]
   [:.namespace-links
    {:display :flex
     :flex-wrap :wrap
     :border-radius "2px"
     :align-items :center
     :justify-content :space-between}
    [:* {}
     [:&:selected {:background-color :fuchsia}]]
    [:input {:display :none
             :width :max-content}]
    [:label {:padding ".50rem .5rem"}]
    [:aside {:padding ".50rem .5rem"}
     [:small {:font-style :italic
              :color :darkgray
              :white-space :nowrap}]]]

   [:.run
    {:margin-bottom "1rem"
     :opacity 0.7}
    [:&.active {:opacity 1}]
    [:p {:margin 0}]
    [:.run-header {:padding ".5rem 1rem"
                   :grid-column-start 1
                   :background-color :initial ;fix
                   :color "#333"              ;fix
                   :border-radius :initial    ;fix
                   :justify-content :initial  ;fix
                   :grid-column-end 3
                   :display :grid
                   :grid-template-columns :subgrid}
     [:p {:grid-column-start 1}]
     [:small {:grid-column-start 2
              :color :gray
              :text-align :right}]]
    [:footer {:padding ".5rem 1rem"
              :grid-column "1 /span 2"
              :grid-row-start 3}]

    [:progress {:grid-column "1 / span 2"
                :background :lime
                :width "100%"
                :height "4px"
                :margin-top ".5rem"
                :margin-bottom ".5rem"}
     [:&.pass
      ["&::-webkit-progress-value" {:background :lime}]
      ["&::-webkit-progress-bar" {:background pass-color}]
      ["&::-moz-progress-bar" {:background :purple}]]
     [:&.fail
      ["&::-webkit-progress-value" {:background :lime}]
      ["&::-webkit-progress-bar" {:background fail-color}]
      ["&::-moz-progress-bar" {:background :purple}]]
     [:&.error
      ["&::-webkit-progress-value" {:background :lime}]
      ["&::-webkit-progress-bar" {:background error-color}]
      ["&::-moz-progress-bar" {:background :purple}]]]]

   [:.test-results {:grid-column "1 / span 2"
                    :line-height "1.6rem"
                    :text-align :justify
                    :margin "0 1rem"
                    :overflow :hidden
                    :font-size "50%"}
    [:.ns {;;:border "1px solid darkslategray"
           :overflow-wrap :anywhere
           :box-shadow "1px 1px 4px #999"}]
    [:.var {:border-right (str "1px solid #ccc")}
     [:&:last-child {:border-style :none}]]
    [:output {:display :inline-box
              :width "1em"}
     [:.pass {:background-color pass-color}]
     [:.fail {:background-color fail-color}]
     [:.error {:background-color error-color}]]]
   [:.ns-run
    {:padding ".5rem 1rem 1rem"
     :margin-bottom "1rem"
     :font-family :sans-serif}
    [:.ns-run--header
     {:background-color :initial
      :color :inherit
      :display :inherit
      :margin-bottom ".5rem"}
     [:h2 {:font-weight :normal
           :margin-bottom ".2rem"
           :font-size "1.1rem"}]
     [:.filename {:color :darkslategray
                  :font-size ".8rem"
                  :font-family :monospace}]]
    [:>div {:display :flex
            :flex-direction :column
            :gap ".5rem"}]
    [:.ns-run--result {:flex-grow 1 :text-align :right}]
    [:.var-name-result {:display :flex
                        :flex-wrap :wrap}]
    [:.ns-run-var
     {:padding-left ".2rem"}
     [:.test-results {:margin "-1px 0 0 0"}]
     [:header
      {:background-color :initial
       :color :inherit
       :border-radius :unset
       :line-height 1.5
       :display :flex}
      [:h3 {:font-weight :normal
            :font-size "1rem"
            :padding "0 1rem 0 0"}]
      [:p {:padding-right ".4rem"}]]
     [:h4 {:font-weight :normal
           :font-size ".8rem"
           :padding-right ".2rem"}]]
    [:.fail {:border-right (str "4px solid "  fail-color)}]
    [:.error {:border-right (str "4px solid " error-color)}]
    [:.pass {:border-right (str "4px solid "  pass-color)}]
    [:h2 :h3 :h4 :p {:margin 0}]
    [:code {:font-family :monospace
            :padding ".2rem"}]
    [:.actual {:color :red
               :font-weight :bold}]]])

(defmacro inline []
  (garden/css
   {:pretty-print? false}
   (concat
    style
    puget-color-styles)))
