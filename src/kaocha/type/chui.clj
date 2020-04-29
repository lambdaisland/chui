(ns kaocha.type.chui
  (:refer-clojure :exclude [symbol])
  (:require [cljs.test :as ct]
            [clojure.spec.alpha :as s]
            [clojure.test :as t]
            [kaocha.hierarchy :as hierarchy]
            [kaocha.output :as output]
            [kaocha.report :as report]
            [kaocha.testable :as testable]
            [kaocha.type :as type]))

(require 'kaocha.cljs.print-handlers
         'kaocha.type.var) ;; (defmethod report/fail-summary ::zero-assertions)

(def server (atom nil))

(defn ensure-server )

(defn client-testable [{:keys [client-id platform]}]
  {::testable/type ::client
   ::testable/id (keyword (str "kaocha.chui/client:" client-id))
   ::testable/meta (meta ns-sym)
   ::testable/desc (str ns-sym)
   ::ns ns-sym
   ::file ns-file})

(defn ns-testable [ns-sym ns-file]
  {::testable/type ::ns
   ::testable/id (keyword (str "cljs:" ns-sym))
   ::testable/meta (meta ns-sym)
   ::testable/desc (str ns-sym)
   ::ns ns-sym
   ::file ns-file})

(defn test-testable [test-name meta]
  {::testable/type ::test
   ::testable/id (keyword (str "cljs:" test-name))
   ::testable/desc (name test-name)
   ::testable/meta meta
   ::test test-name})

(defmethod testable/-load :kaocha.type/chui [testable]
  testable)

(defmethod testable/-load ::ns [testable]
  testable)

(defmethod testable/-load ::test [testable]
  testable)

(defmethod testable/-run :kaocha.type/chui [testable test-plan]
  testable)

(defmethod testable/-run ::ns [testable test-plan]
  testable)

(defmethod testable/-run ::test [testable test-plan]
  testable)

(hierarchy/derive! :kaocha.type/chui :kaocha.testable.type/suite)
(hierarchy/derive! ::ns :kaocha.testable.type/group)
(hierarchy/derive! ::test :kaocha.testable.type/leaf)
(hierarchy/derive! ::timeout :kaocha/fail-type)

(s/def :kaocha.type/chui any? #_(s/keys :req [:kaocha/source-paths
                                              :kaocha/test-paths
                                              :kaocha/ns-patterns]
                                        :opt [:cljs/compiler-options]))

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
