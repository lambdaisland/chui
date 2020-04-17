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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State

(defonce ui-state (reagent/atom {}))

(defn test-plan []
  (let [tests @test-data/test-ns-data]
    (if-let [selected (seq (:selected @runner/state))]
      (select-keys tests selected)
      tests)))

(defn set-ns-select [ns-names]
  (swap! runner/state
         assoc
         :selected
         (set ns-names)))

(defn toggle-ns-select [namespace-name add?]
  (swap! runner/state
         update
         :selected
         (fnil (if add? conj disj) #{})
         namespace-name))

(defn filtered-nss []
  (let [{:keys [query]} @ui-state
        query (if (string? query)
                (str/trim query)
                "")
        nss (map val (sort-by key @test-data/test-ns-data))]
    (if (str/blank? query)
      nss
      (filter #(str/includes? (str (:name %)) query) nss))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def iso-time-pattern "yyyy-MM-dd'T'HH:mm:ss.SSS-00:00")
(def human-time-pattern "yyyy-MM-dd HH:mm:ss")

(defn reltime-str [date]
  (date-relative/format (.getTime date)))

(defn iso-time-str [date]
  (.format (DateTimeFormat. iso-time-pattern) date))

(defn human-time-str [date]
  (.format (DateTimeFormat. human-time-pattern) date))

(defn reltime [date]
  [:time {:dateTime (iso-time-str date)} (reltime-str date)])

(defn summary [sum]
  (let [{:keys [tests pass error fail]} sum]
    (str tests " tests, " (+ pass error fail) " assertions"
         (when (pos-int? error)
           (str ", " error " errors"))
         (when (pos-int? fail)
           (str ", " fail " failures")))))

(defn result-class [summary]
  (cond
    (runner/error? summary) "error"
    (runner/fail? summary) "fail"
    :else "pass"))

(defn result-viz-var [{var-name :name :keys [assertions]}]
  [:span.var
   {:title (str var-name)}
   (for [[i {:keys [type] :as ass}] (map vector (range) assertions)]
     ^{:key (str i)}
     [:span.assertion
      {:class (name type)}
      (case type
        :pass
        " "
        :fail
        "F"
        :error
        "E"
        "?")])])

(defn result-viz [nss selected]
  [:div.result-viz
   (for [{:keys [ns done? vars]} nss]
     ^{:key (str ns)}
     [:span.ns
      {:title ns
       :class (when (or (empty? selected)
                        (contains? selected ns))
                "selected-ns")}
      (for [var-info vars]
        ^{:key (str (:name var-info))}
        [result-viz-var var-info])])])

(defn ns-run [{:keys [ns vars] :as the-ns}]
  (let [{:keys [selected-test only-failing?]} @ui-state
        sum (runner/ns-summary the-ns)]
    [:section.ns-run
     [:h2.section-header (str ns)]
     [:span.filename (:file (:meta (first vars)))]
     [:div
      (for [{:keys [name assertions] :as var-info} vars
            :when (or (not only-failing?) (some (comp #{:fail :error} :type) assertions))
            :let [selected? (= name (:name selected-test))]]
        ^{:key (str name)}
        [:div.ns-run-var.selection-target
         {:class (when selected?
                   "selected")
          :on-click #(swap! ui-state
                            (fn [s]
                              (if selected?
                                (dissoc s :selected-test)
                                (assoc s :selected-test var-info))))}
         (str name)
         [:div.result-viz-var
          [result-viz-var var-info]]])]]))

(defn test-stop-button []
  (let [{:keys [runs]} @runner/state
        test-plan (test-plan)]
    (if (false? (:done? (last runs)))
      [:button.button.stop-tests {:on-click #(runner/terminate! (fn [ctx] (log/info :terminated! ctx)))} "Stop"]
      [:button.button.run-tests {:on-click #(runner/run-tests test-plan)} "Run " (apply + (map (comp count :tests val) test-plan)) " tests"])))

(defn general-toggles []
  [:div.general-toggles
   [:button {:on-click #(swap! runner/state assoc :runs [])} "Clear results"]
   [:input#regexp {:type "checkbox" :name "regexp"}]
   [:label {:for "regexp"} "Regexp"]
   [:input#failing-only {:type "checkbox"
                         :value (:only-failing? @ui-state)
                         :on-click #(swap! ui-state update :only-failing? not)}]
   [:label {:for "failing-only"} "Only show failing tests"]])

(defn header []
  [:header
   [general-toggles]
   [:a.name {:href "https://github.com/lambdaisland/chui"} "lambdaisland.chui"]])

(defn results []
  [:section.column.results
   (for [ns (:nss (:selected-run @ui-state))]
     ^{:key (:ns ns)}
     [ns-run ns])])

(defn history [runs]
  [:section.column.history
   [:div.option
    (let [{:keys [selected]} @runner/state
          {:keys [selected-run only-failing?]} @ui-state]
      (for [{:keys [id nss start done? terminated?] :as run} (reverse runs)
            :let [selected? (= (:id run) (:id selected-run))]]
        (let [sum (runner/run-summary run)]
          ^{:key id}
          [:div.run.selection-target
           {:class (when selected? "selected")}
           [:div {:for id
                  :on-click (fn [_]
                              (swap! ui-state
                                     (fn [s]
                                       (if selected?
                                         (dissoc s :selected-run)
                                         (assoc s :selected-run run)))))}
            [:h2.section-header {:title (str (human-time-str start) " (" (reltime-str start) ")")}
             [summary sum]  " "
             (when-not done?  " (running)")
             (when terminated?  " (aborted)")]
            [result-viz (if only-failing?
                          (filter #(runner/fail? (runner/ns-summary %)) nss)
                          nss) selected]
            ]])))]])

(defn test-selector []
  (reagent/with-let [this (reagent/current-component)
                     _ (add-watch test-data/test-ns-data ::rerender #(reagent/force-update this))]
    (let [{:keys [selected]} @runner/state
          {:keys [query]} @ui-state]
      [:section.column-namespaces
       [:div.search-bar
        [:input {:type "search"
                 ;; :auto-focus true
                 :value query
                 :on-change (fn [e]
                              (let [query (.. e -target -value)]
                                (swap! ui-state
                                       #(assoc % :query query))
                                (set-ns-select
                                 (when-not (str/blank? (str/trim query))
                                   (map :name (filtered-nss))))))
                 :placeholder "name space"}]
        [test-stop-button]]
       [:div.namespace-selector
        (for [{tests :tests
               ns-sym :name
               ns-meta :meta} (filtered-nss)
              :let [ns-str (str ns-sym)
                    test-count (count tests)]
              :when (< 0 test-count)]
          ^{:key ns-str}
          [:div.namespace-links.selection-target
           {:class (when (contains? selected ns-sym) "selected")}
           [:input
            {:id ns-str
             :name ns-str
             :type "checkbox"
             :on-click #(when (str/blank? query)
                          (toggle-ns-select ns-sym (.. % -target -checked)))}]
           [:label {:for ns-str}
            [:span ns-str (when (:test/skip ns-meta)
                            [:span.skip " (skip)"])]
            [:aside test-count (if (= 1 test-count)
                                 " test"
                                 " tests")]]])]])))

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

(defn test-info []
  (let [{:keys [selected-test]} @ui-state
        {:keys [name assertions meta]} selected-test]
    [:section.column.test-info
     [:h2.section-header name]
     (into [:div] (map (fn [m] [:div (pr-str m)])) assertions)
     ]))

(defn col-count []
  (let [{:keys [selected-run selected-test]} @ui-state]
    (cond
      (and selected-run selected-test)
      4
      selected-run
      3
      :else
      2)))

(defn app []
  (let [{:keys [selected runs]} @runner/state
        {:keys [selected-run selected-test]} @ui-state]
    [:div
     [:style (styles/inline)]
     [header]
     [:main
      {:class (str "cols-" (col-count))}
      [test-selector]
      [history runs]
      (when selected-run
        [results])
      (when (and selected-run selected-test)
        [test-info])]]))

(defn render! [element]
  (reagent-dom/render [app] element))
