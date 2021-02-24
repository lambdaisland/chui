(ns lambdaisland.chui.report
  (:require [clojure.pprint :as pprint]
            [kitchen-async.promise :as p]
            [lambdaisland.chui.stacktrace :as stacktrace]
            [lambdaisland.deep-diff2 :as ddiff]
            [lambdaisland.deep-diff2.printer-impl :as printer-impl]
            [lambdaisland.deep-diff2.puget.color.html]
            [lambdaisland.deep-diff2.puget.printer :as puget-printer]
            [reagent.core :as reagent]
            [clojure.string :as str]))

(def html-printer (ddiff/printer {:color-markup :html-classes}))

(defn pprint-str [fipp-doc]
  (with-out-str
    (printer-impl/print-doc fipp-doc html-printer)))

(defn pprint-doc [doc wrap-lines?]
  [:pre (when wrap-lines? {:class "wrap"})
   [:code {:dangerouslySetInnerHTML {:__html (pprint-str doc)}}]])

(defn assertion-type
  "Given a clojure.test event, return the first symbol in the expression inside (is)."
  [m _]
  (if-let [s (and (seq? (:expected m)) (seq (:expected m)))]
    (first s)
    :default))

(defmulti print-expr assertion-type)

(defmethod print-expr :default [m wrap-lines?]
  [pprint-doc
   [:span
    (when (contains? m :expected)
      [:span
       "Expected:" :line
       [:nest (puget-printer/format-doc html-printer (:expected m))]])
    (when (contains? m :expected)
      [:span
       :break
       "Actual:" :line
       [:nest (puget-printer/format-doc html-printer (:actual m))]])]
   wrap-lines?])

(defn print-expr-= [m wrap-lines?]
  (if (and (seq? (second (:actual m)))
           (> (count (second (:actual m))) 2))
    ;; :actual is of the form (not (= ...))

    (let [[_ expected & actuals] (-> m :actual second)]
      [:div
       [:h4 "Expected"]
       (for [[i form] (map vector (range) (drop 2 (:expected m)))]
         ^{:key (str i)}
         [pprint-doc (puget-printer/format-doc html-printer form) wrap-lines?])
       [:h4 "To equal"]
       [pprint-doc (puget-printer/format-doc html-printer expected) wrap-lines?]
       [:h4 "Actual value"]
       (for [[i actual] (map vector (range) actuals)]
         ^{:key (str i)}
         [pprint-doc (puget-printer/format-doc html-printer actual) wrap-lines?])
       (when (and (coll? expected) (every? coll? actuals))
         [:div
          [:h4 "Diff"]
          (for [[i actual] (map vector (range) actuals)]
            ^{:key (str i)}
            [pprint-doc
             (puget-printer/format-doc
              html-printer
              (ddiff/diff expected actual))
             wrap-lines?])])])
    [:div
     (when (contains? m :expected)
       [:div
        [:h4 "Expected"]
        [pprint-doc (puget-printer/format-doc html-printer (:expected m)) wrap-lines?]])
     (when (contains? m :actual)
       [:div
        [:h4 "Actual"]
        [pprint-doc (puget-printer/format-doc html-printer (:actual m)) wrap-lines?]])]))

(defmethod print-expr '= [m wrap-lines?]
  (print-expr-= m wrap-lines?))

(defmethod print-expr '=? [m wrap-lines?]
  (print-expr-= m wrap-lines?))

(defmulti fail-summary :type)

(defmethod fail-summary :default [_ _])

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
  [:<>
   (when (seq testing-contexts)
     [:div.context
      (for [[i ctx] (map vector (range) (reverse testing-contexts))]
        ^{:key (str i)}
        [:div.context (apply str (repeat i "  ")) ctx])])
   (when message
     [:div.message message])])

(defmethod fail-summary :pass [{:keys [testing-contexts testing-vars expected message] :as m}
                               wrap-lines?]
  [:div.fail-summary
   [:aside "PASS"]
   [message-context m]
   [pprint-doc
    (puget-printer/format-doc html-printer expected)
    wrap-lines?]])

(defmethod fail-summary :fail [{:keys [testing-contexts testing-vars message] :as m}
                               wrap-lines?]
  [:div.fail-summary
   [:aside "FAIL"]
   ;; "FAIL in " (testing-vars-str m)
   [message-context m]
   [print-expr m wrap-lines?]])

(defmethod fail-summary :error [{exception :actual
                                 :keys [testing-contexts testing-vars message] :as m}
                                wrap-lines?]
  (reagent/with-let [trace-p (stacktrace/from-error exception)
                     trace (reagent/atom nil)]
    (p/let [t trace-p] (reset! trace t))
    [:div.fail-summary
     [:aside "ERROR"]
     [message-context m]
     [:div (str exception)]
     [:pre (when wrap-lines? {:class "wrap"})
      (if-let [trace @trace]
        (doall
         (for [[{:keys [function file line column]} i] (map vector trace (range))]
           ^{:key i}
           [:<> function " (" [:a {:href file} file] ":" line ":" column ")\n"]))
        (.-stack exception))]
     [:a.bottom-link {:on-click #(do (js/console.log exception) (.preventDefault %)) :href "#"} "Log error"]]))
