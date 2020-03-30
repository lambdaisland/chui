(ns lambdaisland.chui.ui
  (:require [lambdaisland.chui.runner :as runner]
            [lambdaisland.chui.test-data :as test-data]
            [reagent.core :as reagent]
            [reagent.dom :as reagent-dom]))

(defonce test-runs (reagent/atom @runner/test-runs))

(add-watch runner/test-runs ::ui (fn [_ _ _ x] (reset! test-runs x)))

(defn app []
  `[:div
    [:button {:on-click ~#(runner/run-tests @test-data/test-ns-data)} "Test!"]
    ~@(for [{:keys [nss start done?]} @test-runs]
        `^{:key ~(str start)}
        [:div
         [:h1 ~(str start)]
         [:ul
          ~@(for [{:keys [ns vars]} nss
                  {:keys [name assertions]} vars]
              `^{:key ~(str name)}
              [:li ~(str name)
               [:ul
                ~@(for [a assertions]
                    ^{:key (str a)}
                    [:li (str a)])]]
              )]])
    [:p "👍"]])

(defn render! [element]
  (reagent-dom/render [app] element))
