(ns lambdaisland.chui.report
  (:require [clojure.pprint :as pprint]
            [lambdaisland.deep-diff2 :as ddiff]
            [lambdaisland.deep-diff2.puget.printer :as puget-printer]
            [lambdaisland.deep-diff2.printer-impl :as printer-impl]
            [lambdaisland.deep-diff2.puget.color.html]))

(def html-printer (ddiff/printer {:color-markup :html-classes}))

(defn pprint-str [fipp-doc]
  (with-out-str
    (printer-impl/print-doc fipp-doc html-printer)))


(defn pprint-doc [doc]
  [:pre
   [:code {:dangerouslySetInnerHTML {:__html (pprint-str doc)}}]])

(defn assertion-type
  "Given a clojure.test event, return the first symbol in the expression inside (is)."
  [m]
  (if-let [s (and (seq? (:expected m)) (seq (:expected m)))]
    (first s)
    :default))

(defmulti print-expr assertion-type)

(defmethod print-expr :default [m]
  [pprint-doc
   [:span
    "Expected:" :line
    [:nest (puget-printer/format-doc html-printer (:expected m))]
    :break
    "Actual:" :line
    [:nest (puget-printer/format-doc html-printer (:actual m))]]])

(defn print-expression [m]
  (if (and (seq? (second (:actual m)))
           (> (count (second (:actual m))) 2))
    ;; :actual is of the form (not (= ...))

    (let [[_ expected & actuals] (-> m :actual second)]
      [pprint-doc
       [:span
        "Expected:" :line
        [:nest (puget-printer/format-doc html-printer expected)]
        :break
        "Actual:" :line
        (into [:nest]
              (interpose :break)
              (for [actual actuals]
                (puget-printer/format-doc
                 html-printer
                 (if (and (coll? expected) (every? coll? actuals))
                   (ddiff/diff expected actual)
                   actual))))]])
    [pprint-doc
     [:span
      "Expected:" :line
      [:nest (puget-printer/format-doc html-printer (:expected m))]
      :break
      "Actual:" :line
      [:nest (puget-printer/format-doc html-printer (:actual m))]]]))

(defmethod print-expr '= [m]
  (print-expression m))

(defmethod print-expr '=? [m]
  (print-expression m))

(defmulti fail-summary :type)

(defmethod fail-summary :default [_])

(defn testing-vars-str
  "Returns a string representation of the current test. Renders names
  in :testing-vars as a list, then the source file and line of current
  assertion."
  [{:keys [file line testing-vars] :as m}]
  (str
   ;; Uncomment to include namespace in failure report:
   ;;(ns-name (:ns (meta (first *testing-vars*)))) "/ "
   (and (seq testing-vars)
        (reverse (map #(:name (meta %)) testing-vars)))
   (when file
     (str " (" file
          (when line
            (str ":" line))
          ")"))))

(defn message-context [{:keys [testing-contexts testing-vars expected message] :as m}]
  [:div
   (when (seq testing-contexts)
     [:div.context
      (for [[i ctx] (map vector (range) (reverse testing-contexts))]
        ^{:key (str i)}
        [:div "→ " ctx])])
   (when message
     [:div.message message])])

(defmethod fail-summary :pass [{:keys [testing-contexts testing-vars expected message] :as m}]
  [:div.fail-summary
   [:h3 "PASS"]
   [message-context m]
   [pprint-doc
    (puget-printer/format-doc html-printer expected)]])

(defmethod fail-summary :fail [{:keys [testing-contexts testing-vars message] :as m}]
  [:div.fail-summary
   [:h3 "FAIL"]
   ;; "FAIL in " (testing-vars-str m)
   [message-context m]
   [print-expr m]])

(defmethod fail-summary :error [{exception :actual
                                 :keys [testing-contexts testing-vars message] :as m}]
  [:div.fail-summary
   [:h3 "ERROR"]
   [message-context m]
   [:div (str exception)]
   [:pre (.-stack exception)]
   ])
