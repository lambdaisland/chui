(ns lambdaisland.chui.ui
  (:require [goog.date :as gdate]
            [goog.date.relative :as date-relative]
            [lambdaisland.chui.runner :as runner]
            [lambdaisland.chui.test-data :as test-data]
            [lambdaisland.chui.report :as report]
            [lambdaisland.glogi :as log]
            [reagent.core :as reagent]
            [reagent.dom :as reagent-dom]
            [clojure.string :as str]
            [lambdaisland.deep-diff2 :as ddiff])
  (:require-macros [lambdaisland.chui.styles :as styles])
  (:import (goog.i18n DateTimeFormat)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State

(defonce ui-state (reagent/atom {}))

(defn test-plan []
  (let [tests @test-data/test-ns-data]
    (if (str/blank? (:query @ui-state))
      tests
      (select-keys tests (:selected @runner/state)))))

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
  (let [{:keys [query regexp?]} @ui-state
        query (if (string? query)
                (str/trim query)
                "")
        nss (map val (sort-by key @test-data/test-ns-data))]
    (cond
      (str/blank? query)
      nss

      regexp?
      (filter #(re-find (js/RegExp. query) (str (:name %))) nss)

      :else
      (filter #(str/includes? (str (:name %)) query) nss))))

(defn selected-run []
  (or (:selected-run @ui-state)
      (last (:runs @runner/state))))

(defn set-state-from-location []
  (let [params (js/URLSearchParams. js/location.search)
        match (.get params "match")
        include (.get params "include")]
    (cond
      match
      (swap! ui-state assoc :query match :regexp? true)
      include
      (swap! ui-state assoc :query include :regexp? false))))

(defn push-state-to-location []
  (let [{:keys [query regexp?]} @ui-state
        params (js/URLSearchParams.)]
    (when (not (str/blank? query))
      (.set params (if regexp? "match" "include") query)
      (js/window.history.pushState
       {:query query :regexp? regexp?}
       "lambdaisland.chui"
       (str "?" params)))))

(defn set-query! [query]
  (swap! ui-state
         #(assoc % :query query))
  (set-ns-select
   (when-not (str/blank? (str/trim query))
     (map :name (filtered-nss))))
  (push-state-to-location))

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
  [:output.var
   {:title (str var-name)}
   (for [[i {:keys [type]}] (map vector (range) assertions)]
     ^{:key (str i)}
     [:output.assertion {:class (name type)}])])

(defn result-viz [nss selected]
  [:section.test-results-ns
   (for [{:keys [ns vars]} nss]
     ^{:key (str ns)}
     [:span.ns
      {:title ns
       :class (when (or (empty? selected)
                        (contains? selected ns))
                "selected-ns")}
      (for [var-info vars]
        ^{:key (str (:name var-info))}
        [result-viz-var var-info])])])

(defn ns-run [{:keys [ns vars]
               :as the-ns}]
  (let [{:keys [selected-test only-failing?]} @ui-state
        sum (runner/ns-summary the-ns)
        error? (runner/error? sum)
        fail? (runner/fail? sum)]
    (when (or (not only-failing?) fail?)
      [:article.ns-run
       [:header.ns-run--header
        [:h2 (str ns)]
        [:small.filename (:file (:meta (first vars)))]]
       [:div
        (for [{var-name :name :keys [assertions] :as var-info} vars
              :when (or (not only-failing?) (some (comp #{:fail :error} :type) assertions))
              :let [selected? (= var-name (:name selected-test))]]
          ^{:key (str var-name)}
          [:article.ns-run-var.selection-target
           {:class (str/join " "
                             [(when selected? "selected")
                              (cond
                                error? "error"
                                fail?  "fail"
                                :else  "pass")])
            :on-click #(swap! ui-state
                              (fn [s]
                                (if selected?
                                  (dissoc s :selected-test)
                                  (assoc s :selected-test var-info))))}
           [:header
            [:h3.ns-run--assertion (name var-name)]
            [:p.ns-run--result [:strong (cond error? "Error"
                                              fail?  "Fail"
                                              :else  "Pass")]]]
           [:output
            [:h4 ""]
            [:code.expected ""]
            [:h4 ""]
            [:code.actual ""]]])]])))

(defn test-stop-button []
  (let [{:keys [runs]} @runner/state
        test-plan (test-plan)
        test-count (apply + (map (comp count :tests val) test-plan))]
    (if (false? (:done? (last runs)))
      [:button.button.stop-tests {:on-click #(runner/terminate! (fn [ctx] (log/info :terminated! ctx)))} "Stop"]
      [:button.button.run-tests
       {:on-click #(runner/run-tests test-plan)
        :disabled (= 0 test-count)}
       "Run " test-count " tests"])))

(defn general-toggles []
  [:div.general-toggles
   [:button {:on-click #(swap! runner/state assoc :runs [])} "Clear results"]
   [:input#regexp
    {:type "checkbox"
     :on-change (fn [e]
                  (swap! ui-state assoc :regexp? (.. e -target -checked))
                  (push-state-to-location))
     :checked (boolean (:regexp? @ui-state))}]
   [:label {:for "regexp"} "Regexp search"]
   [:input#failing-only
    {:type "checkbox"
     :checked (boolean (:only-failing? @ui-state))
     :on-change #(swap! ui-state assoc :only-failing? (.. % -target -checked))}]
   [:label {:for "failing-only"} "Only show failing tests"]])

(defn header []
  [:header
   [general-toggles]
   [:a.name {:href "https://github.com/lambdaisland/chui"} "lambdaisland.chui"]])

(defn results []
  [:section.column.results
   (for [ns (:nss (selected-run))]
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
          [:article.run.selection-target
           {:class (when selected? "selected")}
           [:header.run-header
            [:progress {:max (:test-count run) :value (:tests (runner/run-summary run))}]
            [:p (reltime-str start)]
            [:small
             (when-not done? "Running")
             (when terminated? "Aborted")]]
           [:section.test-results {:for id
                                   :on-click (fn [_]
                                               (swap! ui-state
                                                      (fn [s]
                                                        (if selected?
                                                          (dissoc s :selected-run)
                                                          (assoc s :selected-run run)))))}
            [result-viz (if only-failing?
                          (filter #(runner/fail? (runner/ns-summary %)) nss)
                          nss) selected]]
           [:footer
            [:p [summary sum]]]])))]])

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
                                (set-query! query)))
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
     (into [:div]
           (map (fn [m] [report/fail-summary m]))
           assertions)]))

(defn col-count []
  (let [runs? (seq (:runs @runner/state))
        {:keys [selected-test]} @ui-state]
    (cond
      (and runs? selected-test)
      4
      runs?
      3
      :else
      2)))

(defn app []
  (let [{:keys [selected runs]} @runner/state
        {:keys [selected-test]} @ui-state
        runs? (seq runs)]
    [:div
     [:style (styles/inline)]
     [header]
     [:main
      {:class (str "cols-" (col-count))}
      [test-selector]
      [history runs]
      (when runs?
        [results])
      (when (and runs? selected-test)
        [test-info])]]))

(defn render! [element]
  (reagent-dom/render [app] element))
