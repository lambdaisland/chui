(ns lambdaisland.chui.report
  (:require [clojure.pprint :as pprint]
            [lambdaisland.deep-diff2 :as ddiff]
            [lambdaisland.deep-diff2.printer-impl :as printer-impl]
            [lambdaisland.deep-diff2.puget.color.html]))

;; (def narrow-printer (ddiff/printer {:color-markup :html-inline}))

;; (printer-impl/format-doc (ddiff/diff {:a 1 :b 2} {:a 1 :b 3}) narrow-printer)

;; (deep-diff/diff {:a 1} {:b 1})

;; (defn pprint [code]
;;   [:pre
;;    [:code
;;     (with-out-str (clojure.pprint/pprint code))]])

;; (defn assertion-type
;;   "Given a clojure.test event, return the first symbol in the expression inside (is)."
;;   [m]
;;   (if-let [s (and (seq? (:expected m)) (seq (:expected m)))]
;;     (first s)
;;     :default))

;; (defmulti format-expr assertion-type)

;; (defmethod format-expr :default [m]
;;   [:div
;;    (when (contains? m :expected)
;;      [:span "Expected:" [pprint (:expected m)]])
;;    (when (contains? m :actual)
;;      [:span "Actual:" [pprint (:actual m)]])])

;; (defn format-expression [m]
;;   (if (and (seq? (second (:actual m)))
;;            (> (count (second (:actual m))) 2))
;;     ;; :actual is of the form (not (= ...))
;;     (let [[_ expected & actuals] (-> m :actual second)]
;;       [:div
;;        [:div "Expected:"]
;;        [pprint expected]
;;        [:div "Actual:"]
;;        (into [:div]
;;              (for [actual actuals]
;;                (output/format-doc ((jit lambdaisland.deep-diff/diff) expected actual)
;;                                   printer)))])
;;     (output/print-doc
;;      [:span
;;       "Expected:" :line
;;       [:nest (output/format-doc (:expected m) printer)]
;;       :break
;;       "Actual:" :line
;;       [:nest (output/format-doc (:actual m) printer)]])))

;; (defmethod format-expr '= [m]
;;   (format-expression m))

;; (defmethod format-expr '=? [m]
;;   (format-expression m))

;; (defmulti fail-summary :type :hierarchy #'hierarchy/hierarchy)

;; (defmethod fail-summary :default [_])

;; (defmethod fail-summary :kaocha/fail-type [{:keys [testing-contexts testing-vars] :as m}]
;;   (println (str "\n" (output/colored :red "FAIL") " in") (testing-vars-str m))
;;   (when (seq testing-contexts)
;;     (println (str/join " " (reverse testing-contexts))))
;;   (when-let [message (:message m)]
;;     (println message))
;;   (if-let [expr (::printed-expression m)]
;;     (print expr)
;;     (format-expr m))
;;   (print-output m))

;; (defmethod fail-summary :error [{:keys [testing-contexts testing-vars] :as m}]
;;   (println (str "\n" (output/colored :red "ERROR") " in") (testing-vars-str m))
;;   (when (seq testing-contexts)
;;     (println (str/join " " (reverse testing-contexts))))
;;   (when-let [message (:message m)]
;;     (println message))
;;   (if-let [expr (::printed-expression m)]
;;     (print expr)
;;     (when-let [actual (:actual m)]
;;       (print "Exception: ")
;;       (if (throwable? actual)
;;         (stacktrace/print-cause-trace actual t/*stack-trace-depth*)
;;         (prn actual))))
;;   (print-output m))
