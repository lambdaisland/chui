(ns lambdaisland.chui.ui
  (:require [lambdaisland.chui.runner :as runner]
            [lambdaisland.chui.test-data :as test-data]
            [reagent.core :as reagent]
            [reagent.dom :as reagent-dom]
            [goog.date :as gdate]
            [goog.date.relative :as date-relative])
  (:require-macros [lambdaisland.chui.styles :as styles])
  (:import (goog.i18n DateTimeFormat)))

(defonce test-runs (reagent/atom @runner/test-runs))

(add-watch runner/test-runs ::ui (fn [_ _ _ x] (reset! test-runs x)))

(def inst-pattern "yyyy-MM-dd'T'HH:mm:ss.SSS-00:00")

(defn reltime [date]
  [:time {:datetime (.format (DateTimeFormat. inst-pattern) (js/Date.))}
   (date-relative/format (.getTime date))])

(defn summary [sum]
  (let [{:keys [tests pass error fail]} sum]
    (str tests " tests"
         (when (pos-int? error)
           (str ", " error " errors"))
         (when (pos-int? fail)
           (str ", " fail " failures")))))

(defn comparison [{:keys [actual expected]}]
  [:div
   [:pre [:code (pr-str expected)]]
   [:div "▶" [:pre [:code (pr-str actual)]]]])

(defn error-comparison [{:keys [expected actual]}]
  [:div
   [:pre [:code (pr-str expected)]]
   [:div
    [:span "Error: "]
    (when actual
      [:span (.-message actual)])
    #_(when actual
        (let [error-number (next-error-count)]
          (js/console.log "CLJS Test Error #" error-number)
          (js/console.error actual)
          [:div :view-stacktrace
           (str "For stacktrace: See error number " error-number " in console")]))]])

(defmulti assertion :type)

(defmethod assertion :pass [_]
  "pass")

(defmethod assertion :fail [m]
  [:div "fail " (:message m)
   [comparison m]])

(defmethod assertion :error [m]
  [:div "error " (:message m)
   [comparison m]])

(defn ns-run [{:keys [ns vars]}]
  [:div.ns
   [:h2 (str ns) [:span.filename (:file (:meta (first vars)))]]
   [:ul
    (for [{:keys [name assertions]} vars]
      ^{:key (str name)}
      [:li (str name)
       [:ul
        (for [a assertions]
          ^{:key (str a)}
          [:li [assertion a]])]])]])

(defn test-run [{:keys [nss start done?] :as run}]
  (let [sum (runner/summary run)]
    [:section.run {:class (cond
                            (pos-int? (:error sum)) "error"
                            (pos-int? (:fail sum)) "fail"
                            :else "pass")}
     [:h1 (when-not done? [:span [:span.spinner] " "] ) [summary sum] " "  [reltime start]]
     (for [ns nss]
       [ns-run ns])
     ]))

(defn app []
  [:main
   [:style (styles/inline)]
   [:button {:on-click #(runner/run-tests @test-data/test-ns-data)} "Test!"]
   (for [run (reverse @test-runs)]
     ^{:key (str (:start run))}
     [test-run run])
   [:p "👍"]])

(defn render! [element]
  (reagent-dom/render [app] element))
