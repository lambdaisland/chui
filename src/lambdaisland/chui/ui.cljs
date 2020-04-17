(ns lambdaisland.chui.ui
  (:require [goog.date :as gdate]
            [goog.date.relative :as date-relative]
            [lambdaisland.chui.runner :as runner]
            [lambdaisland.chui.test-data :as test-data]
            [lambdaisland.glogi :as log]
            [reagent.core :as reagent]
            [reagent.dom :as reagent-dom]
            [clojure.string :as str])
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
       [ns-run ns])]))

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

(defn test-stop-button []
  (let [{:keys [runs]} @runner/state]
    (if (false? (:done? (last runs)))
      [:button.button.stop-tests {:on-click #(runner/terminate! (fn [ctx] (log/info :terminated! ctx)))} "Stop"]
      [:button.button.run-tests {:on-click #(runner/run-tests)} "Run"])))

(defn app []
  (let [{:keys [selected runs]} @runner/state]
    [:main
     [:style (styles/inline)]
     [test-stop-button]
     [test-selector selected]
     (for [run (reverse runs)]
       ^{:key (str (:start run))}
       [test-run run])]))

(defn general-toggles []
  [:div.general-toggles
   [:input#regexp {:type "checkbox" :name "regexp"}]
   [:label {:for "regexp"} "Regexp"]
   [:input#passing {:type "checkbox" :name "passing"}]
   [:label {:for "passing"} "Passing"]
   [:input#history {:type "checkbox" :name "history"}]
   [:label {:for "history"} "History"]])

(defn columns-control []
  [:div.columns-control
   [:input#one-column {:type "radio" :name "columns" :value "One"}]
   [:label {:for "one-column"} "One"]
   [:input#two-columns {:type "radio" :name "columns" :value "Two"}]
   [:label {:for "two-columns"} "Two"]
   [:input#three-columns {:type "radio" :name "columns" :value "Three"}]
   [:label {:for "three-columns"} "Three"]])

(defn density-control []
  [:div.density-control
   [:input#dense {:type "radio" :name "whitespace" :value "Dense"}]
   [:label {:for "dense"} "Dense"]
   [:input#cozy {:type "radio" :name "whitespace" :value "Cozy"}]
   [:label {:for "cozy"} "Cozy"]])

(defn theme-control []
  [:div.theme-control
   [:input#theme {:type "checkbox" :name "theme"}]
   [:label {:for "theme"} "Toggle theme"]])

(defn header []
  [:header
   [general-toggles]
   [:div.interface-controls
    [columns-control]
    [density-control]
    [theme-control]]
   [:a.name {:href "/info"} "\\ ˈchüāi?\\"]])

(defn select-namespace [namespace-name]
  (swap! runner/state
         assoc
         :selected
         [namespace-name]))

(defonce ui-state (reagent/atom {}))

(defn filtered-ns-names []
  (let [{:keys [query]} @ui-state
        query (if (string? query)
                (str/trim query)
                "")
        nss (sort (keys @test-data/test-ns-data))]
    (if (str/blank? query)
      nss
      (filter #(str/includes? (str %) query) nss))))

(defn test-selector-2 [selected]
  (reagent/with-let [this (reagent/current-component)]
    (add-watch test-data/test-ns-data ::rerender #(reagent/force-update this))
    [:section.namespaces
     [:div.search-bar
      [:input {:type "search"
               ;; :auto-focus true
               :value (:query @ui-state)
               :on-change (fn [e]
                            (let [query (.. e -target -value)]
                              (swap! ui-state assoc :query query)
                              (swap! runner/state
                                     assoc
                                     :selected
                                     (when-not (str/blank? (str/trim query))
                                       (filtered-ns-names)))))
               :placeholder "name space"}]
      [test-stop-button]]
     [:div.namespace-selector
      (for [ns (filtered-ns-names)]
        ^{:key (str ns)}
        [:div.namespace-links
         [:input
          {:id (str ns)
           :name (str ns)
           :type "checkbox"
           :on-click #(select-namespace ns)}]
         [:label {:for (str ns)} (str ns)]
         [:aside (str "3" " runs")]])]]))
        

(defn history [runs]
  [:section.history
   [:div.option
    (for [{:keys [id nss start done?] :as run} (reverse runs)]
      (let [sum (runner/run-summary run)]
        ^{:key id}
        [:div
         [:input.toggle {:id id
                         :type "radio"
                         :name "history"
                         :on-change #(swap! ui-state assoc :selected-run run)}]
         [:label {:for id}
          (when-not done? [:span [:span.spinner] " "] )
          [summary sum] " "
          [reltime start]]]))]])

(defn results []
  [:section.results
   (for [ns (:nss (:selected-run @ui-state))]
     ^{:key (:ns ns)}
     [ns-run ns])
   #_[:div
      [:p "aa-test"]
      [:code "(= 123 124)"]
      [:code "(= 123 124)"]]])

(defn test-info []
  [:section.test-info
   [:article
    [:header
     [:p "Diff/Stacktrace"]]]])


(defn app2 []
  (let [{:keys [selected runs]} @runner/state]
    [:div
     [:style (styles/inline)]
     [header]
     [:main
      [test-selector-2]
      [history runs]
      [results]
      [test-info]]]))

(defn render! [element]
  (reagent-dom/render [app2] element))
