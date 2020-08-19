(ns lambdaisland.chui.remote
  {:dev/always true}
  (:require [cljs.pprint :as pp :include-macros true]
            [cljs.test :as t]
            [clojure.browser.repl :as browser-repl]
            [clojure.string :as str]
            [cognitect.transit :as transit]
            [goog.dom :as gdom]
            [goog.dom :as gdom]
            [goog.object :as gobj]
            [kitchen-async.promise :as p]
            [lambdaisland.chui.interceptor :as intor]
            [lambdaisland.chui.runner :as runner]
            [lambdaisland.chui.test-data :as test-data]
            [lambdaisland.chui.stacktrace :as stacktrace]
            [lambdaisland.funnel-client :as funnel-client]
            [lambdaisland.glogi :as log]
            [lambdaisland.glogi.console :as glogi-console])
  (:require-macros [lambdaisland.chui.remote.macros :refer [working-directory]])
  (:import [goog.string StringBuffer]))

(defn ^:export init [_])

(log/set-levels '{:glogi/root :finest})

(glogi-console/install!)

(defn record-handler [type]
  (transit/write-handler (constantly type)
                         (fn [val]
                           (into {} val))))

(swap! funnel-client/whoami
       assoc
       :lambdaisland.chui.remote? true)

(swap! funnel-client/transit-write-handlers
       merge

       {:default (transit/write-handler
                  (fn [o]
                    (str (type o)))
                  (fn [o]
                    (str o)))

        cljs.core/Var (transit/write-handler
                       (constantly "var")
                       (fn [rep] (meta rep)))}

       (when (exists? matcher-combinators.model/Mismatch)
         {^:cljs.analyzer/no-resolve matcher-combinators.model.Mismatch
          (record-handler "matcher-combinators.model.Mismatch")})
       (when (exists? matcher-combinators.model/Missing)
         {^:cljs.analyzer/no-resolve matcher-combinators.model.Missing
          (record-handler "matcher-combinators.model.Missing")})
       (when (exists? matcher-combinators.model/Unexpected)
         {^:cljs.analyzer/no-resolve matcher-combinators.model.Unexpected
          (record-handler "matcher-combinators.model.Unexpected")})
       (when (exists? matcher-combinators.model/InvalidMatcherType)
         {^:cljs.analyzer/no-resolve matcher-combinators.model.InvalidMatcherType
          (record-handler "matcher-combinators.model.InvalidMatcherType")})
       (when (exists? matcher-combinators.model/InvalidMatcherContext)
         {^:cljs.analyzer/no-resolve matcher-combinators.model.InvalidMatcherContext
          (record-handler "matcher-combinators.model.InvalidMatcherContext")})
       (when (exists? matcher-combinators.model/FailedPredicate)
         {^:cljs.analyzer/no-resolve matcher-combinators.model.FailedPredicate
          (record-handler "matcher-combinators.model.FailedPredicate")})
       (when (exists? matcher-combinators.model/TypeMismatch)
         {^:cljs.analyzer/no-resolve matcher-combinators.model.TypeMismatch
          (record-handler "matcher-combinators.model.TypeMismatch")}))

(declare socket)

(defn send! [message]
  (funnel-client/send socket message))

;; TODO: replace with deep-diff
#_
(defn pretty-print-failure [m]
  (let [buffer (StringBuffer.)]
    (binding [humane-print/*sb* buffer
              *out*             (pp/get-pretty-writer (StringBufferWriter. buffer))]
      (let [{:keys [type expected actual diffs message] :as event}
            (humane-print/convert-event m)
            print-expected (fn [actual]
                             (humane-print/rprint "Expected:\n  ")
                             (pp/pprint expected *out*)
                             (humane-print/rprint "Actual:\n  ")
                             (pp/pprint actual *out*))]
        (if (seq diffs)
          (doseq [[actual [a b]] diffs]
            (print-expected actual)
            (humane-print/rprint "Diff:\n  ")
            (if a
              (do (humane-print/rprint "- ")
                  (pp/pprint a *out*)
                  (humane-print/rprint "  + "))
              (humane-print/rprint "+ "))
            (when b
              (pp/pprint b *out*)))
          (print-expected actual)))
      (str humane-print/*sb*))))

(defn cljs-test-msg [m]
  {:type :cljs.test/message
   :cljs.test/message m
   :cljs.test/testing-contexts (:testing-contexts (t/get-current-env))})

#_(assoc m :kaocha.report/printed-expression
         (pretty-print-failure m))

(defn wrap-report [report]
  (fn [m]
    (send!
     (case (:type m)
       :fail (cljs-test-msg m)
       :error
       (let [error (:actual m)]
         (p/let [trace (stacktrace/from-error error)]
           (let [rendered (str/join
                           "\n"
                           (for [{:keys [function file line column]} trace]
                             (str function " (" file ":" line ":" column ")")))]
             (log/error :just-making-sure rendered)
             (cljs-test-msg
              (assoc m
                     :kaocha.report/printed-expression (str error "\n" rendered "\n")
                     :kaocha.report/error-type (str "js/" (.-name error))
                     :message (or (:message m) (.-message error)))))))
       ;;:else
       (cljs-test-msg m)))
    (report m)))

(defmulti handle-message :type)

(defmethod handle-message :default [msg]
  (log/debug :unhandled-message msg))

(defmethod handle-message :ping [msg]
  (send! {:type :pong}))

(defmethod handle-message :start-run [msg]
  (when (runner/running?)
    (runner/terminate!))
  (runner/install-custom-reporter)
  (runner/add-test-run! (-> (runner/test-run)
                            (assoc :test-count (:test-count msg)
                                   :remote? true)
                            (update :report wrap-report)))
  (send! {:type :run-started :reply-to (:id msg)}))

(defmethod handle-message :finish-run [msg]
  (runner/update-run assoc
                     :end (js/Date.)
                     :done? true)
  (send! {:type :run-finished :reply-to (:id msg)}))

(defn execute-chain [intors]
  (-> (:ctx (runner/current-run))
      (intor/enqueue intors)
      intor/execute))

(defmethod handle-message :start-ns [{:keys [ns] :as msg}]
  (p/let [ctx (execute-chain (runner/begin-ns-intors ns (get @test-data/test-ns-data ns)))]
    (runner/update-run assoc :ctx ctx)
    (send! {:type :ns-started :reply-to (:id msg)})))

(defmethod handle-message :finish-ns [{:keys [ns] :as msg}]
  (p/let [ctx (execute-chain (runner/end-ns-intors ns (get @test-data/test-ns-data ns)))]
    (runner/update-run assoc :ctx ctx)
    (send! {:type :ns-finished :reply-to (:id msg)})))

(defmethod handle-message :run-test [{:keys [test] :as msg}]
  (let [ns        (symbol (namespace test))
        ns-data   (get @test-data/test-ns-data ns)
        test-data (some #(when (= test (:name %))
                           %)
                        (:tests ns-data))]
    (p/let [ctx (execute-chain (runner/wrap-each-fixtures
                                ns
                                (runner/var-intors test-data)
                                (:each-fixtures ns-data)))]
      (runner/update-run assoc :ctx ctx)
      (send! {:type :test-finished
              :reply-to (:id msg)
              :summary (runner/var-summary (->> (runner/current-run)
                                                :nss
                                                (some #(when (= (:ns %) ns) %))
                                                :vars
                                                (some #(when (= (:name %) test) %))))}))))

(defn scrub-var-data [vars-data]
  (map #(dissoc % :test :var :ns) vars-data))

(defn scrub-test-data [test-data]
  (map (comp #(-> %
                  (dissoc :once-fixtures :each-fixtures)
                  (update :tests scrub-var-data)) val)
       test-data))

(defmethod handle-message :fetch-test-data [msg]
  (send! {:type :test-data
          :reply-to (:id msg)
          :test-data (scrub-test-data
                      @test-data/test-ns-data)}))

(defn connect! []
  (set! socket (funnel-client/connect {:on-message (fn [_ msg] (handle-message msg))})))

(defn disconnect! []
  (when socket
    (log/info :msg "Disconnecting funnel")
    (funnel-client/disconnect! socket)
    (set! socket nil)))

(defonce init-conn (connect!))


(when (exists? js/document)
  (set! js/document.title (:id @funnel-client/whoami)))

#_
(defonce ui ;; temporary, for testing
  (do
    (when-not (.getElementById js/document "chui-container")
      (let [app (gdom/createElement "div")]
        (gdom/setProperties app #js {:id "chui-container"})
        (gdom/append js/document.body app)))

    (ui/render! (.getElementById js/document "chui-container"))))
