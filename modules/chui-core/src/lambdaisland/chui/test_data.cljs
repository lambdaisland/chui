(ns lambdaisland.chui.test-data
  (:require [lambdaisland.glogi :as log]
            [cljs.test])
  (:require-macros [lambdaisland.chui.test-data :refer [capture-test-data!]]))

(def test-ns-data (atom nil))
