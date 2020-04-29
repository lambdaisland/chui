(ns kaocha.type.chui
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
            [kaocha.type :as type])
  (:import java.util.UUID))

(require 'kaocha.cljs.print-handlers
         'kaocha.type.var) ;; (defmethod report/fail-summary ::zero-assertions)

(defn client-testable [{:keys [client-id platform agent-id]}]
  {::testable/type ::client
   ::testable/id (keyword (str "kaocha.chui/client:" client-id))
   ::testable/meta {}
   ::testable/desc platform
   :kaocha.chui.client/client-id client-id
   :kaocha.chui.client/agent-id client-id})

(defn test-testable [client-id {:keys [name meta]}]
  {::testable/type ::test
   ::testable/id (keyword (str client-id ":" name))
   ::testable/name name
   ::testable/desc (str name)
   ::testable/meta meta
   ::test name})

(defn ns-testable [client-id {:keys [name meta tests]}]
  {::testable/type ::ns
   ::testable/id (keyword (str client-id ":" name))
   ::testable/name name
   ::testable/meta meta
   ::testable/desc (str name)
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

(defmethod testable/-load :kaocha.type/chui [{:chui/keys [compile-hook
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
           :kaocha.test-plan/tests (testable/load-testables testables))))

(comment
  (require 'kaocha.repl)
  (kaocha.repl/run :unit)
  (kaocha.repl/test-plan )

  )

(defmethod testable/-load ::client [testable]
  (let [client-id (:kaocha.chui.client/client-id testable)
        chan      (async/chan)
        msg-id    (UUID/randomUUID)]
    (server/tap-client client-id chan)
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
                 (server/untap-client client-id chan)
                 %)})))

(defmethod testable/-run :kaocha.type/chui [testable test-plan]
  testable)

(defmethod testable/-run ::client [testable test-plan]
  testable)

(defmethod testable/-run ::ns [testable test-plan]
  testable)

(defmethod testable/-run ::test [testable test-plan]
  testable)

(hierarchy/derive! :kaocha.type/chui :kaocha.testable.type/suite)
(hierarchy/derive! ::client :kaocha.testable.type/group)
(hierarchy/derive! ::ns :kaocha.testable.type/group)
(hierarchy/derive! ::test :kaocha.testable.type/leaf)
(hierarchy/derive! ::timeout :kaocha/fail-type)

(s/def :kaocha.type/chui any? #_(s/keys :req [:kaocha/source-paths
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
