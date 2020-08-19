(ns lambdaisland.chui.stacktrace
  (:require [stacktrace-js :as stacktrace]
            [stacktrace-gps :as gps]
            [kitchen-async.promise :as p]
            [reagent.core :as reagent]
            [clojure.string :as str]))

(defn unmunge [s]
  (when s
    (-> (str s)
        (str/replace "$" ".")
        (str/replace "_STAR_" "*")
        (str/replace "_BANG_" "!")
        (str/replace "_" "-"))))

(defn from-error [error]
  (p/let [trace (stacktrace/fromError error)]
    (for [frame trace]
      {:function (unmunge (.getFunctionName frame))
       :file (.getFileName frame)
       :line (.getLineNumber frame)
       :column (.getColumnNumber frame)})))



;; (p/let [t (from-error js/xxx)]
;;   (def xxx t))

;; (p/let [trace (stacktrace/fromError js/xxx)
;;         trace' (p/all
;;                 (map #(.pinpoint (gps.) %)
;;                      trace))]
;;   (def ttt trace'))
