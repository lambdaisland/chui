(ns lambdaisland.chui.demo
  (:require [lambdaisland.chui-demo.a-test]
            [lambdaisland.chui.ui :as ui]
            [lambdaisland.chui.test-info :as test-info]))

(def ui (ui/render! (.getElementById js/document "app")))


(test-info/capture-test-data!)

((:test-fn (first @test-info/tests)))
