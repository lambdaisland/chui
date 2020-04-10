(ns lambdaisland.chui.demo
  (:require [lambdaisland.chui-demo.a-test]
            [lambdaisland.chui.runner :as runner]
            [lambdaisland.chui.ui :as ui]
            [lambdaisland.chui.test-data :as test-data]
            [lambdaisland.glogi :as log]
            [lambdaisland.glogi.console :as glogi-console]))

(glogi-console/install!)

(log/set-levels
 '{:glogi/root :debug
   lambdaisland :all
   lambdaisland.chui.interceptor :error})

(defn start []
  ())

(defn stop [done]
  (done))

(defn ^:export init []
  (ui/render! (.getElementById js/document "app"))
  (test-data/capture-test-data!)
  (start))
