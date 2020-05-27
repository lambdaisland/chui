(ns lambdaisland.chui.remote.macros
  (:require [clojure.java.io :as io]))

(defmacro working-directory []
  (str (.getAbsolutePath (io/file ""))))
