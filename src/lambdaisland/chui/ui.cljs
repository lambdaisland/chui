(ns lambdaisland.chui.ui
  (:require [goog.date :as gdate]
            [goog.date.relative :as date-relative]
            [lambdaisland.chui.runner :as runner]
            [lambdaisland.chui.test-data :as test-data]
            [lambdaisland.glogi :as log]
            [reagent.core :as reagent]
            [reagent.dom :as reagent-dom])
  (:require-macros [lambdaisland.chui.styles :as styles])
  (:import (goog.i18n DateTimeFormat)))

(def inst-pattern "yyyy-MM-dd'T'HH:mm:ss.SSS-00:00")

(defn reltime [date]
  [:time {:dateTime (.format (DateTimeFormat. inst-pattern) (js/Date.))}
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
  [:span {:style {:color "green"}} "•"])

(defmethod assertion :fail [m]
  [:div (:message m)
   [comparison m]])

(defmethod assertion :error [m]
  [:div "error " (:message m)
   [comparison m]])

(defn result-class [summary]
  (cond
    (runner/error? summary) "error"
    (runner/fail? summary) "fail"
    :else "pass"))

(defn ns-run [{:keys [ns vars] :as the-ns}]
  (let [sum (runner/ns-summary the-ns)]
    [:div.ns {:class (result-class sum)}
     [:h2 (str ns) [:span.filename (:file (:meta (first vars)))]]
     (when (runner/fail? sum)
       [:ul
        (for [{:keys [name assertions]} vars]
          (into ^{:key (str name)}
                [:li (str name)]
                (map #(vector assertion %))
                assertions))])]))

(defn test-run [{:keys [nss start done?] :as run}]
  (let [sum (runner/run-summary run)]
    [:section.run {:class (result-class sum)}
     [:h1 (when-not done? [:span [:span.spinner] " "] ) [summary sum] " "  [reltime start]]
     (for [ns nss]
       ^{:key (:ns ns)}
       [ns-run ns])
     ]))

(defn test-selector [selected]
  (reagent/with-let [this (reagent/current-component)]
    (add-watch test-data/test-ns-data ::rerender #(reagent/force-update this))
    [:select {:value (or selected "ALL")
              :on-change (fn [e]
                           (let [v (.. e -target -value)]
                             (swap! runner/state
                                    assoc
                                    :selected
                                    (when (not= v "ALL")
                                      [(symbol v)]))))}
     [:option {:value "ALL"} "all tests"]
     (for [ns (sort (keys @test-data/test-ns-data))]
       ^{:key ns}
       [:option {:value ns} ns])]))

(defn app []
  (let [{:keys [selected runs]} @runner/state]
    [:main
     [:style (styles/inline)]
     (if (false? (:done? (last runs)))
       [:button {:on-click #(runner/terminate! (fn [ctx] (log/info :terminated! ctx)))} "Stop test run"]
       [:button {:on-click #(runner/run-tests)} "Run Tests"])
     [test-selector selected]
     (for [run (reverse runs)]
       ^{:key (str (:start run))}
       [test-run run])]))

(defn render! [element]
  (reagent-dom/render [app] element))
