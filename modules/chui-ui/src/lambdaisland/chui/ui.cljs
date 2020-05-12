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
  #_(:require-macros [lambdaisland.chui.styles :as styles])
  (:import (goog.i18n DateTimeFormat)))

(def styles
  #_(styles/inline)
  "*{box-sizing:border-box}html{font-family:sans-serif;height:100vh}body{margin:0;height:100vh;overflow:hidden}#app{height:100vh}#chui{height:100vh;overflow:hidden}.top-bar{margin:.2rem;display:flex;justify-content:space-between;align-items:center}.button{border:0}.button:hover{cursor:pointer}.general-toggles{display:flex;align-items:center}.general-toggles input,.general-toggles label{margin-right:.5rem;vertical-align:text-bottom}.clear{margin-right:.5rem;padding:.5rem}.name{text-decoration:none;padding-right:.3rem}.card{background-color:#f5f7fa;border:1px solid #d4dde9;margin-bottom:.2rem}.inner-card{background-color:white;border:1px solid #d4dde9}main{display:flex;padding-left:.2rem;height:100%;overflow:hidden;gap:.2rem}section{flex:1}.column{height:100%;overflow:scroll}.last-column{flex:2;overflow-y:scroll}.fieldset{border:1px solid black;margin-top:.3rem;margin-bottom:.3rem}input[type=\"search\"]{padding:.5rem;border:0;width:100%;line-height:1.5}.selection-target:hover{background-color:white}.selection-target.selected{background-color:white}.section-header{font-weight:normal;font-size:1.1rem;margin:0}.test-info{padding:.5rem .2rem;margin-bottom:.2rem}.test-info .inner-card{padding:.3rem .2rem;margin:.2rem 0}.test-info .assertion{position:relative;overflow-y:auto}.test-info .context,.test-info .message{margin-bottom:.2rem}.test-info .pass{border-right:4px solid #ccc}.test-info .fail{border-right:4px solid #eeea0b}.test-info .error{border-right:4px solid crimson}.test-info aside{position:absolute;top:0;right:0;font-variant-caps:all-small-caps;padding:.2rem .2rem}.test-info h4{margin:0;font-weight:normal;font-variant-caps:all-small-caps}.test-info .bottom-link{text-decoration:none}.namespaces+ul{padding-left:1.5rem;line-height:1.7rem}.toggle{position:absolute;left:-100vw}.namespace-selector{display:flex;flex-direction:column;margin-top:.5rem}.search-bar{display:grid;grid-template-columns:4fr minmax(26%,1fr);grid-auto-flow:column;position:sticky;top:0}.run-tests{line-height:.9}.namespace-links{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;line-height:1.5}.namespace-links input{display:none;width:max-content}.namespace-link{display:inline-flex;width:100%;justify-content:space-between;flex-wrap:wrap;align-items:baseline}.namespace-link small{color:gray}.run{opacity:.5}.run.active{opacity:1;background-color:white;border:1px solid darkgray}.run output{margin-bottom:.2rem}.run:hover{opacity:1}.run p{margin:0}.run footer{padding:.5rem .2rem;grid-column:1 /span 2;grid-row-start:3}.run .test-results{margin:0 .2rem}.progress{grid-column:1 / span 2;background:whitesmoke;width:100%;height:1rem;margin-top:.5rem;margin-bottom:.5rem;border:0}.run-header{padding:.5rem .2rem}.run-header p{grid-column-start:1}.run-header small{grid-column-start:2;text-align:right}.test-results{background-color:white}.test-results .ns{overflow-wrap:anywhere}.test-results .var{margin-right:.2rem;line-height:1.5;margin-bottom:.2rem}.test-results .var:last-child{border-style:none}.test-results output{display:inline-block}.test-results output .pass{background-color:#ccc}.test-results output .fail{background-color:#eeea0b}.test-results output .error{background-color:crimson}.ns-run{padding:.5rem .2rem .5rem;margin-bottom:.2rem;font-family:sans-serif}.ns-run .ns-run--header{display:inherit;margin-bottom:.5rem}.ns-run .ns-run--header h2{font-weight:normal;font-size:1rem;margin-bottom:.2rem}.ns-run .ns-run--header .filename{font-family:monospace;font-size:.8rem}.ns-run>div{display:flex;flex-direction:column;gap:.2rem}.ns-run .ns-run--result{flex-grow:1;text-align:right}.ns-run .var-name-result{display:flex;flex-wrap:wrap}.ns-run .ns-run-var{padding-left:.2rem}.ns-run .ns-run-var header{border-radius:unset;line-height:1.5;display:flex}.ns-run .ns-run-var header h3{font-weight:normal;font-size:1rem;padding:0 1rem 0 0}.ns-run .ns-run-var header p{padding-right:.4rem}.ns-run .ns-run-var h4{font-weight:normal;padding-right:.2rem}.ns-run h2,.ns-run h3,.ns-run h4,.ns-run p{margin:0}.ns-run code{font-family:monospace;padding:.2rem}code .class-delimiter{color:#a3685a}code .class-name{color:#a3685a}code .nil{color:#4d4d4c}code .boolean{color:#4d4d4c}code .number{color:#4271ae}code .character{color:#a3685a}code .string{color:#3e999f}code .keyword{color:#4271ae}code .symbol{color:#3e999f}code .delimiter{color:#8959a8}code .function-symbol{color:#8959a8}code .tag{color:#a3685a}code .insertion{color:#718c00}code .deletion{color:#c82829}"
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State

(defonce ui-state (reagent/atom {:only-failing? true}))
(defonce runner-state (reagent/atom {}))

;; We don't want the runner to depend on reagent, but we do want to watch it for
;; changes, so instead of making runner/state an ratom we do this. Corrolary:
;; use runner/state when swapping, use runner-state when derefing.
(add-watch runner/state
           ::runner-state
           (fn [_ _ _ state]
             (reset! runner-state state)))

(declare run-tests)

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

(defn test-plan []
  (let [tests @test-data/test-ns-data]
    (cond
      (seq (:selected @runner-state))
      (select-keys tests (:selected @runner-state))

      (not (str/blank? (:query @ui-state)))
      (into {} (map (juxt :name identity)) (filtered-nss))

      :else
      (into {}
            (remove (comp :test/skip :meta val))
            tests))))

(defn selected-run []
  (or (:selected-run @ui-state)
      (last (:runs @runner-state))))

(defn failing-tests []
  (filter #(runner/fail? (runner/var-summary %))
          (mapcat :vars (:nss (selected-run)))))

(defn selected-tests []
  (let [{:keys [selected-tests]} @ui-state]
    (set
     (if (seq selected-tests)
       (filter #(some #{(:name %)} selected-tests)
               (mapcat :vars (:nss (selected-run))))
       (failing-tests)))))

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
      (.set params (if regexp? "match" "include") query))
    (js/window.history.pushState
     {:query query :regexp? regexp?}
     "lambdaisland.chui"
     (str "?" params))))

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
     [:output.assertion {:class (name type)} " ​"])])

(defn result-viz [nss selected]
  [:section.test-results
   (interpose
    " "
    (for [{:keys [ns vars]} nss]
      ^{:key (str ns)}
      [:span.ns
       {:title ns
        :class (when (or (empty? selected)
                         (contains? selected ns))
                 "selected-ns")}
       (for [var-info vars]
         ^{:key (str (:name var-info))}
         [result-viz-var var-info])]))])

(defn run-results [{:keys [ns vars]
                    :as the-ns}]
  (let [{:keys [only-failing?]} @ui-state
        selected-tests (selected-tests)
        sum (runner/ns-summary the-ns)
        fail? (runner/fail? sum)]
    (when (or (not only-failing?) fail?)
      [:article.ns-run.card
       [:header.ns-run--header
        [:h2 (str ns)]
        [:small.filename (:file (:meta (first vars)))]]
       [:div
        (for [{var-name :name :keys [assertions] :as var-info} vars
              :when (or (not only-failing?) (some (comp #{:fail :error} :type) assertions))
              :let [selected? (some (comp #{var-name} :name) selected-tests)
                    sum (runner/var-summary var-info)
                    error? (runner/error? sum)
                    fail? (runner/fail? sum)]]
          ^{:key (str var-name)}
          [:article.ns-run-var.selection-target.inner-card
           {:class (str/join " "
                             [(when selected? "selected")
                              (cond
                                error? "error"
                                fail?  "fail"
                                :else  "pass")])
            :on-click #(swap! ui-state
                              (fn [s]
                                (assoc s :selected-tests
                                       (if selected?
                                         (remove #{var-name} (map :name selected-tests))
                                         #{var-name}))))}
           [:header.result-var-card
            [:div.var-name-result
             [:h3.ns-run--assertion (name var-name)]
             [:output.test-results [:span.ns [result-viz-var var-info]]]]
            [:p.ns-run--result [:strong (cond error? "Error"
                                              fail?  "Fail"
                                              :else  "Pass")]]]])]])))

(defn test-stop-button []
  (let [{:keys [runs]} @runner-state
        test-plan (test-plan)
        test-count (apply + (map (comp count :tests val) test-plan))]
    (if (false? (:done? (last runs)))
      [:button.button.stop-tests {:on-click #(runner/terminate! (fn [ctx] (log/info :terminated! ctx)))} "Stop"]
      [:button.button.run-tests
       {:on-click #(run-tests)
        :disabled (= 0 test-count)}
       "Run " test-count " tests"])))

(defn general-toggles []
  [:div.general-toggles
   [:button.button.clear {:on-click #(swap! runner/state assoc :runs [])} "Clear results"]
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
  [:header.top-bar
   [general-toggles]
   [:a.name {:href "https://github.com/lambdaisland/chui"} "lambdaisland.chui"]])

(defn results []
  [:section.column
   [:div.results
    (for [ns (:nss (selected-run))]
      ^{:key (:ns ns)}
      [run-results ns])]])

(defn history [runs]
  [:section.column.history
   (let [{:keys [selected]} @runner-state
         {:keys [only-failing?]} @ui-state
         selected-run (selected-run)]
     (for [{:keys [id nss start done? terminated?] :as run} (reverse runs)
           :let [selected? (= id (:id selected-run))
                 active? (and (not selected-run) (= id (:id (last runs))))]]
       (let [sum (runner/run-summary run)]
         ^{:key id}
         [:article.run.selection-target.card
          {:class (cond
                    selected? "selected active"
                    active? "active")
           :on-click (fn [_]
                       (swap! ui-state
                              (fn [s]
                                (assoc s :selected-run run))))}
          [:header.run-header
           [:progress.progress {:max (:test-count run)
                                :value (:tests (runner/run-summary run))}]
           [:p (reltime-str start)]
           [:small
            (when-not done? "Running")
            (when terminated? "Aborted")]]
          [result-viz (if only-failing?
                        (filter #(runner/fail? (runner/ns-summary %)) nss)
                        nss) selected]
          [:footer
           [:p [summary sum]]]])))])

(defn- filter'n-run []
  (let [{:keys [query]} @ui-state]
    [:div.search-bar.card
     [:input {:type :search
              :value query
              :on-change (fn [e]
                           (let [query (.. e -target -value)]
                             (set-query! query)))
              :on-key-up (fn [e]
                           (when (= (.-key e) "Enter")
                             (run-tests)))
              :placeholder "namespace"}]
     [test-stop-button]]))

(defn test-selector []
  (reagent/with-let [this (reagent/current-component)
                     _ (add-watch test-data/test-ns-data ::rerender #(reagent/force-update this))]
    (let [{:keys [selected]} @runner-state
          {:keys [query]} @ui-state]
      [:section.column.namespaces
       [filter'n-run]
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
           [:label.namespace-link {:for ns-str}
            [:span ns-str]
            [:small test-count (if (= 1 test-count)
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

(defn test-assertions [{var-name :name :keys [assertions]}]
  (reagent/with-let [pass? (comp #{:pass} :type)
                     show-passing? (reagent/atom false)]
    [:div.test-info.card
     [:h2.section-header var-name]
     (into [:div]
           (comp
            (if @show-passing?
              identity
              (remove pass?))
            (map (fn [m]
                   [:div.inner-card.assertion {:class (name (:type m))}
                    [report/fail-summary m]])))
           assertions)
     (let [pass-count (count (filter pass? assertions))]
       (when (and (not @show-passing?) (< 0 pass-count))
         [:a.bottom-link {:on-click #(do (reset! show-passing? true) (.preventDefault %)) :href "#"}
          "Show " pass-count " passing assertions"]))]))

(defn assertion-details []
  [:section.column.last-column
   (if-let [tests (seq (selected-tests))]
     (map (fn [test]
            ^{:key (:name test)}
            [test-assertions test])
          tests) ; Felipe fix me :)
     [:p "All tests pass!"])])

(defn col-count []
  (let [runs? (seq (:runs @runner-state))]
    (cond
      runs?
      4
      :else
      2)))

(defn app []
  (let [{:keys [runs]} @runner-state
        runs? (seq runs)]
    [:div#chui
     [:style styles]
     [header]
     [:main
      [test-selector]
      [history runs]
      (when runs?
        [results])
      (when runs?
        [assertion-details])]]))

(defn run-tests []
  (let [tests (test-plan)]
    (when (seq tests)
      (runner/run-tests tests)
      (swap! ui-state dissoc :selected-run))))

(defn terminate! [done]
  (runner/terminate! done))

(defn render! [element]
  (set-state-from-location)
  (reagent-dom/render [app] element))
