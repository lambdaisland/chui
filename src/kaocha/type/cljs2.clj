(ns kaocha.type.cljs2
  (:refer-clojure :exclude [symbol])
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [clojure.test :as t]
            [kaocha.chui.log :as log]
            [kaocha.chui.server :as server]
            [kaocha.chui.channel-grinder :as channel-grinder]
            [kaocha.hierarchy :as hierarchy]
            [kaocha.output :as output]
            [kaocha.report :as report]
            [kaocha.testable :as testable]
            [kaocha.type :as type]
            [cljs.repl :as repl]
            [kaocha.hierarchy :as kaocha]
            [clojure.string :as str])
  (:import java.util.UUID))

(require 'kaocha.cljs.print-handlers
         'kaocha.type.var) ;; (defmethod report/fail-summary ::zero-assertions)

(defn client-testable [{:keys [client-id platform agent-id]}]
  {::testable/type ::client
   ::testable/id (keyword (str "kaocha.chui/client:" client-id))
   ::testable/meta {}
   ::testable/desc platform
   ::testable/aliases [(keyword (str/lower-case (first (str/split platform #" "))))]
   :kaocha.chui.client/client-id client-id
   :kaocha.chui.client/agent-id client-id})

(defn test-testable [client-id {:keys [name meta]}]
  {::testable/type ::test
   ::testable/id (keyword (str client-id ":" name))
   ::testable/name name
   ::testable/desc (str name)
   ::testable/meta meta
   ::testable/aliases [(keyword (str name))]
   ::test name})

(defn ns-testable [client-id {:keys [name meta tests]}]
  {::testable/type ::ns
   ::testable/id (keyword (str client-id ":" name))
   ::testable/name name
   ::testable/meta meta
   ::testable/desc (str name)
   ::testable/aliases [(keyword name)]
   ::ns name
   :kaocha.test-plan/tests
   (map (partial test-testable client-id) tests)})

(defn resolve-fn [f]
  (cond
    (qualified-symbol? f)
    (requiring-resolve f)

    (list? f)
    (eval f)

    :else
    f))

;; compile-hook :: suite -> suite
;; invokes cljs compilation, makes sure our client and any test nss are injected
;; can set extra info on the suite, like the location of js or html file

;; connect-hook :: suite -> seq<client-id>
;; responsible for making sure clients are launched, connected, and ready.
;; Returns a list of client-ids

(defmacro with-tap [[client-id chan] & body]
  `(try
     (server/tap-client ~client-id ~chan)
     ~@body
     (finally
       (server/untap-client ~client-id ~chan))))

(defmethod testable/-load :kaocha.type/cljs2 [{:chui/keys [compile-hook
                                                           connect-hook
                                                           server-opts
                                                           clients-hook]
                                               :or        {compile-hook identity
                                                           connect-hook identity
                                                           clients-hook 'kaocha.chui/all-connected-clients}
                                               :as        suite}]
  (when-not (server/running?)
    (log/config :test-suite suite)
    (log/info :server-not-running {:msg "Kaocha server not running, starting it now."})
    (server/start! server-opts))
  (let [compile-hook (resolve-fn compile-hook)
        connect-hook (resolve-fn connect-hook)
        clients-hook (resolve-fn clients-hook)
        suite        (compile-hook suite)
        _            (connect-hook suite)
        client-ids   (clients-hook suite)
        testables    (map (comp client-testable server/client) client-ids)]
    (assoc suite
           ::testable/aliases [:cljs]
           ::testable/parallelizable? true
           :kaocha.test-plan/tests (testable/load-testables testables))))

(defmethod testable/-load ::client [testable]
  (let [client-id (:kaocha.chui.client/client-id testable)
        chan      (async/chan)
        msg-id    (UUID/randomUUID)]
    (with-tap [client-id chan]
      (server/send! client-id {:type :fetch-test-data :id msg-id})
      (channel-grinder/execute
       chan
       {:init     testable
        :handlers {:test-data (fn [msg testable]
                                (if (= msg-id (:reply-to msg))
                                  (assoc testable :kaocha.test-plan/tests
                                         (map (partial ns-testable client-id) (:test-data msg)))
                                  testable))
                   :timeout (fn [testable]
                              (throw (ex-info "Timeout while fetching test data"
                                              {::fetch-test-data :timeout
                                               :client
                                               (select-keys testable
                                                            [:kaocha.testable/id
                                                             :kaocha.testable/desc])})))}
        :result #(when (:kaocha.test-plan/tests %)
                   #_(server/untap-client client-id chan)
                   %)}))))


(defmethod testable/-run :kaocha.type/cljs2 [testable test-plan]
  (t/do-report {:type :begin-test-suite})
  (let [results (testable/run-testables (:kaocha.test-plan/tests testable) test-plan)]
    (t/do-report {:type :end-test-suite})
    (assoc testable :kaocha.result/tests results)))

(defmethod testable/-run ::client [{::keys [chan] :as testable} test-plan]
  (t/do-report {:type :kaocha/begin-group})
  (log/debug ::client (::testable/id testable))
  (let [client-id    (:kaocha.chui.client/client-id testable)
        chan         (async/chan)
        send!        (partial server/send! client-id)
        listen       (partial channel-grinder/execute chan)
        start-msg-id (UUID/randomUUID)
        end-msg-id   (UUID/randomUUID)]
    (with-tap [client-id chan]
      (send! {:type       :start-run
              :id         start-msg-id
              :test-count (->> testable
                               testable/test-seq
                               (filter (comp #{::test} ::testable/type))
                               count)})
      (listen {:handlers
               {:run-started (fn [msg ctx]
                               (cond-> ctx
                                 (= start-msg-id (:reply-to msg))
                                 (assoc :done? true)))}})
      (let [ns-tests (map #(assoc %
                                  ::send! send!
                                  ::listen listen)
                          (:kaocha.test-plan/tests testable))
            ns-tests (testable/run-testables ns-tests test-plan)]
        (send! {:type :finish-run :id end-msg-id})
        (listen {:handlers
                 {:run-finished (fn [msg ctx]
                                  (cond-> ctx
                                    (= end-msg-id (:reply-to msg))
                                    (assoc :done? true)))}})
        (t/do-report {:type :kaocha/end-group})
        (assoc testable :kaocha.result/tests ns-tests)))))

(defmethod testable/-run ::ns [{::keys [send! listen ns] :as testable} test-plan]
  (t/do-report {:type :begin-test-ns})
  (log/debug ::ns (::testable/id testable))
  (let [start-msg-id (UUID/randomUUID)]
    (send! {:type :start-ns :id start-msg-id :ns ns})
    (listen {:handlers {:ns-started (fn [msg ctx]
                                      (cond-> ctx
                                        (= start-msg-id (:reply-to msg))
                                        (assoc :done? true)))}}))
  (let [var-tests  (map #(assoc %
                                ::send! send!
                                ::listen listen)
                        (:kaocha.test-plan/tests testable))
        var-tests  (testable/run-testables var-tests test-plan)
        end-msg-id (UUID/randomUUID)]
    (send! {:type :finish-ns :id end-msg-id})
    (listen {:handlers {:ns-finished (fn [msg ctx]
                                       (cond-> ctx
                                         (= end-msg-id (:reply-to msg))
                                         (assoc :done? true)))}})

    (t/do-report {:type :end-test-ns})
    (assoc testable :kaocha.result/tests var-tests)))

(defmethod testable/-run ::test [{::keys [send! listen test] :as testable} test-plan]
  (t/do-report {:type :begin-test-var})
  (log/debug ::test (::testable/id testable))
  (let [msg-id (UUID/randomUUID)
        _ (send! {:type :run-test :id msg-id :test test})
        testable (listen
                  {:init testable
                   :result #(when (:kaocha.result/count %) %)
                   :handlers
                   {:cljs.test/message
                    (fn [msg testable]
                      (t/do-report (:cljs.test/message msg))
                      testable)
                    :test-finished
                    (fn [{:keys [summary reply-to]} testable]
                      (cond-> testable
                        (= msg-id reply-to)
                        (assoc :kaocha.result/count 1
                               :kaocha.result/test 1
                               :kaocha.result/pass (:pass summary 0)
                               :kaocha.result/fail (:fail summary 0)
                               :kaocha.result/error (:error summary 0)
                               :kaocha.result/pending 0)))}})]
    (t/do-report {:type :end-test-var})
    testable))

(hierarchy/derive! :kaocha.type/cljs2 :kaocha.testable.type/suite)
(hierarchy/derive! ::client :kaocha.testable.type/group)
(hierarchy/derive! ::ns :kaocha.testable.type/group)
(hierarchy/derive! ::test :kaocha.testable.type/leaf)
(hierarchy/derive! ::timeout :kaocha/fail-type)

(s/def :kaocha.type/cljs2 any? #_(s/keys :req [:kaocha/source-paths
                                               :kaocha/test-paths
                                               :kaocha/ns-patterns]
                                         :opt [:cljs/compiler-options]))

(s/def ::client any?)
(s/def ::ns any?)
(s/def ::test any?)

(defmethod report/dots* ::timeout [m]
  (t/with-test-out
    (print (output/colored :red "T"))
    (flush)) )

(comment
  (require 'kaocha.repl)

  (kaocha.repl/run :lambdaisland.chui-demo.a-test/aa-test)

  (kaocha.repl/run [:firefox-2 :foo.bar/baz])

  (repl/run :lambdaisland.chui-demo.a-test/aa-test)

  :kaocha.testable/id
  :c862dae5-5e27-42ee-a1eb-f75d3b791d56:lambdaisland.chui-demo.a-test/aa-test,

  :kaocha.testable/aliases [:lambdaisland.chui-demo.a-test/aa-test
                            :aa-test]

  :unit
  :editor

  (kaocha.repl/config)
  (map ::testable/aliases (kaocha.testable/test-seq (kaocha.repl/test-plan)))


  )

(comment
  (require 'kaocha.repl)

  (kaocha.repl/run :cljs {:kaocha/tests [{:kaocha.testable/type :kaocha.type/cljs
                                          :kaocha.testable/id   :cljs
                                          :kaocha/source-paths  ["src"]
                                          :kaocha/test-paths    ["test/cljs"]
                                          :kaocha/ns-patterns   [".*-test$"]
                                          :cljs/timeout 50000
                                          :cljs/repl-env 'cljs.repl.browser/repl-env
                                          }]
                          :kaocha.plugin.capture-output/capture-output? false
                          :kaocha/reporter ['kaocha.report/documentation]})

  (require 'kaocha.type.var)

  )
