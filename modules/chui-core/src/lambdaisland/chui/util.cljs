(ns lambdaisland.chui.util
  (:require [goog.object :as gobj]))

(defn thenable? [^js x]
  (when-let [then (gobj/get x "then")]
    (fn? then)))
