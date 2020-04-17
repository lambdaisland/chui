(ns lambdaisland.chui.report
  (:require [clojure.pprint :as pprint]
            [lambdaisland.deep-diff2 :as ddiff]
            [lambdaisland.deep-diff2.puget.printer :as puget-printer]
            [lambdaisland.deep-diff2.printer-impl :as printer-impl]
            [lambdaisland.deep-diff2.puget.color.html]))

(def html-printer (ddiff/printer {:color-markup :html-inline}))

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
                 (ddiff/diff expected actual))))]])
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

(defmethod fail-summary :fail [{:keys [testing-contexts testing-vars] :as m}]
  [:div
   "FAIL"
   ;; (println (str "\n" (output/colored :red "FAIL") " in") (testing-vars-str m))
   ;; (when (seq testing-contexts)
   ;;   (println (str/join " " (reverse testing-contexts))))
   ;; (when-let [message (:message m)]
   ;;   (println message))
   ;; (if-let [expr (::printed-expression m)]
   ;;   (print expr)
   ;;   (print-expr m))
   ;; (print-output m)
   [print-expr m]]
  )

(defmethod fail-summary :error [{:keys [testing-contexts testing-vars] :as m}]
  [:div
   "ERROR"]
  ;; (println (str "\n" (output/colored :red "ERROR") " in") (testing-vars-str m))
  ;; (when (seq testing-contexts)
  ;;   (println (str/join " " (reverse testing-contexts))))
  ;; (when-let [message (:message m)]
  ;;   (println message))
  ;; (if-let [expr (::printed-expression m)]
  ;;   (print expr)
  ;;   (when-let [actual (:actual m)]
  ;;     (print "Exception: ")
  ;;     (if (throwable? actual)
  ;;       (stacktrace/print-cause-trace actual t/*stack-trace-depth*)
  ;;       (prn actual))))
  ;; (print-output m)
  )
