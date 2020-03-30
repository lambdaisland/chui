(ns lambdaisland.chui.demo
  (:require [lambdaisland.chui-demo.a-test]
            [lambdaisland.chui.ui :as ui]
            [lambdaisland.chui.test-data :as test-data]
            [lambdaisland.glogi :as log]
            [lambdaisland.glogi.console :as glogi-console]))

(log/set-levels '{:glogi/root :all
                  lambdaisland.chui.interceptor :error})

(glogi-console/install!)

(def ui
  (ui/render! (.getElementById js/document "app")))

(test-data/capture-test-data!)

(comment
  @test-data/test-ns-data)
