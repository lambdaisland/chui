(ns lambdaisland.chui.ui
  (:require [lambdaisland.chui.runner :as runner]
            [lambdaisland.chui.test-data :as test-data]
            [reagent.core :as reagent]
            [reagent.dom :as reagent-dom]))

(defn render! [element]
  (reagent-dom/render [:p "👍"] element))
