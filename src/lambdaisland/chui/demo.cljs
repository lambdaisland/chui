(ns lambdaisland.chui.demo
  (:require [lambdaisland.chui-demo.a-test]
            [lambdaisland.chui.ui :as ui]
            [lambdaisland.chui.test-data :as test-data]
            [lambdaisland.glogi :as log]
            [lambdaisland.glogi.console :as glogi-console]))

(glogi-console/install!)

(log/set-levels
 '{:glogi/root :debug
   lambdaisland :all
   lambdaisland.chui.interceptor :error})

(def ui
  (ui/render! (.getElementById js/document "app")))

(test-data/capture-test-data!)
