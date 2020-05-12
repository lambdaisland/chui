(ns lambdaisland.chui.styles
  (:require [garden.core :as garden]
            [garden.stylesheet :as stylesheet]
            [garden.selectors :as selectors]
            [garden.color :as color]))

(def fail-color  "#eeea0b")
(def error-color "crimson")
(def pass-color  "#ccc")

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

;; (s/defpseudoelement -moz-progress-bar)
;; (s/defpseudoelement -webkit-progress-bar)
;; (s/defpseudoelement -webkit-progress-value)

(def style
  [[:html {:font-family "sans-serif"
           :height "100vh"}]

   [:body {:margin 0
           :height "100vh"
           :overflow :hidden}]

   [:#chui-container
    {:height "100vh"}
    [:* {:box-sizing :border-box}]]

   [:#chui {:height "100vh"
            :overflow :hidden}]

   [:.top-bar {:margin ".2rem"
               :display :flex
               :justify-content :space-between
               :align-items :center}]

   [:.button {:border 0}
    [:&:hover {:cursor :pointer}]]

   [:.general-toggles {:display :flex
                       :align-items :center}
    [:input :label {:margin-right ".5rem"
                    :vertical-align :text-bottom}]]

   [:.clear {:margin-right ".5rem"
             :padding ".5rem"}]

   [:.name {:text-decoration :none
            :padding-right ".3rem"}]

   [:.card {:background-color "#f5f7fa"
            :border "1px solid #d4dde9"
            :margin-bottom ".2rem"}]

   [:.inner-card {:background-color "white"
                  :border "1px solid #d4dde9"}]

   [:main {:display :flex
           :padding-left ".2rem"
           :height "100%"
           :overflow :hidden
           :gap ".2rem"}]

   [:section {:flex 1}]

   [:.column {:height "100%"
              :overflow :scroll}]

   [:.last-column {:flex 2
                   :overflow-y :scroll}]

   [:.fieldset {:border "1px solid black"
                :margin-top ".3rem"
                :margin-bottom ".3rem"}]

   [(search-input)
    {:padding ".5rem"
     :border "none"
     :width "100%"
     :line-height 1.5}]

   ;; [(search-input "::placeholder") {}]

   [:.selection-target                  ;FIX
    [:&:hover {:background-color :white}]
    [:&.selected {:background-color :red}]]

   [:.section-header {:font-weight :normal
                      :font-size "1.1rem"
                      :margin 0}]

   [:.test-info {:padding ".5rem .2rem"
                 :margin-bottom ".2rem"}
    [:.inner-card {:padding ".3rem .2rem"
                   :margin ".2rem 0"}]
    [:.assertion {:position :relative
                  :overflow-y :auto}]
    [:.context :.message {:margin-bottom ".2rem"}]
    [:.pass {:border-right (str "4px solid " pass-color)}]
    [:.fail {:border-right (str "4px solid " fail-color)}]
    [:.error {:border-right (str "4px solid " error-color)}]
    [:aside {:position :absolute
             :top 0
             :right 0
             :font-variant-caps :all-small-caps
             :padding ".2rem .2rem"}]
    [:h4 {:margin 0
          :font-weight :normal
          :font-variant-caps :all-small-caps}]

    [:.bottom-link {:text-decoration :none}]]

   [:.namespaces [:+ul {:padding-left "1.5rem"
                        :line-height "1.7rem"}]]
   [:.toggle {:position :absolute
              :left "-100vw"}]
   [:.namespace-selector {:display :flex
                          :flex-direction :column
                          :margin-top ".5rem"}]
   [:.active {}]
   [:.search-bar {:display :grid
                  :grid-template-columns "4fr minmax(26%, 1fr)"
                  :grid-auto-flow :column
                  :position :sticky
                  :top 0}]

   [:.run-tests {:line-height ".9"}
    [:&:hover :&:active {}]
    [:&:hover:disabled {}]]

   [:.stop-tests {}
    [:&:hover {}]]

   [:.namespace-links {:display :flex
                       :flex-wrap :wrap
                       :align-items :center
                       :justify-content :space-between
                       :line-height 1.5}
    [:* {}
     [:&:selected {}]]
    [:input {:display :none
             :width :max-content}]]
   [:.namespace-link {:display :inline-flex
                      :width "100%"
                      :justify-content :space-between
                      :flex-wrap :wrap
                      :align-items :baseline}
    [:span [:line-height 1.2]]
    [:small {:color :gray}]]

   [:.run {:opacity 0.5}
    [:&.active {:opacity 1
                :background-color :white
                :border "1px solid darkgray"}]
    [:output {:margin-bottom ".2rem"}]
    [:&:hover {:opacity 1}]
    [:p {:margin 0}]
    [:footer {:padding ".5rem .2rem"
              :grid-column "1 /span 2"
              :grid-row-start 3}]
    [:.test-results {:margin "0 .2rem"}]]

   [:.progress {:grid-column "1 / span 2"
                :background :whitesmoke
                :width "100%"
                :height "1rem"
                :margin-top ".5rem"
                :margin-bottom ".5rem"
                :border :none}]
   ;; [:.moz-progress-error {:background :whitesmoke}
   ;;  [(&) (& -moz-progress-bar) {:background error-color}]]
   ;; [:.webkit-progress-error {:background :whitesmoke}
   ;;  [(& -webkit-progress-bar {:background :whitesmoke})]
   ;;  [(& -webkit-progress-value {:background error-color})]]

   [:.run-header {:padding ".5rem .2rem"}
    [:p {:grid-column-start 1}]
    [:small {:grid-column-start 2
             :text-align :right}]]

   [:.test-results {:background-color :white}
    [:.ns {:overflow-wrap :anywhere}]
    [:.var {:margin-right ".2rem"
            :line-height 1.5
            :margin-bottom ".2rem"}
     [:&:last-child {:border-style :none}]]
    [:output {:display :inline-block}
     [:.pass {:background-color pass-color}]
     [:.fail {:background-color fail-color}]
     [:.error {:background-color error-color}]]]

   [:.ns-run {:padding ".5rem .2rem .5rem"
              :margin-bottom ".2rem"
              :font-family :sans-serif}

    [:.ns-run--header {:display :inherit
                       :margin-bottom ".5rem"}
     [:h2 {:font-weight :normal
           :font-size "1rem"
           :margin-bottom ".2rem"}]
     [:.filename {:font-family :monospace
                  :font-size ".8rem"}]]

    [:>div {:display :flex
            :flex-direction :column
            :gap ".2rem"}]

    [:.ns-run--result {:flex-grow 1 :text-align :right}]

    [:.var-name-result {:display :flex
                        :flex-wrap :wrap}]

    [:.ns-run-var {:padding-left ".2rem"}
     [:header {:border-radius :unset
               :line-height 1.5
               :display :flex}
      [:h3 {:font-weight :normal
            :font-size "1rem"
            :padding "0 1rem 0 0"}]
      [:p {:padding-right ".4rem"}]]
     [:h4 {:font-weight :normal
           :padding-right ".2rem"}]]

    [:h2 :h3 :h4 :p {:margin 0}]
    [:code {:font-family :monospace
            :padding ".2rem"}]
    [:.actual {}]]])

(defmacro inline []
  (garden/css
   {:pretty-print? false}
   (concat
    style
    puget-color-styles)))
