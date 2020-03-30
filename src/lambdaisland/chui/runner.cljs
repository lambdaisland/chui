(ns lambdaisland.chui.runner
  (:require [cljs.test :as t]
            [lambdaisland.chui.interceptor :as intor]
            [lambdaisland.chui.test-data :as test-data]
            [lambdaisland.glogi :as log]
            [kitchen-async.promise :as p]))

(defonce test-runs (atom []))

(defn new-test-run! []
  (dec (count (swap! test-runs conj {}))))

(defn empty-env [opts]
  (merge
   {:report-counters {:test 0 :pass 0 :fail 0 :error 0}
    :testing-vars ()
    :testing-contexts ()
    :formatter pr-str}
   opts))

(defonce cljs-test-report t/report)

(defn legacy-reporter [reporter]
  (fn [m]
    ((get-method cljs-test-report [reporter (:type m)]) m)))

(defn report [m]
  (doseq [f (:reporters (t/get-current-env))]
    (f m)))


(defn cljs-test-intor
  "Turn a function which may return a cljs.test IAsyncTest into a promise-based interceptor.

  An IAsyncTest is a special type of object which is callable. It takes a single
  argument: a continuation callback, which is a zero-arity function. In effect
  it is like a promise which does not yield a value, but simply signals that
  some process has completed.

  IAsyncTest values are created using the `cljs.test/async` macro, which may be
  used in tests (deftest) and fixtures to implement asynchrony. "
  [f]
  {:name ::cljs-test-intor
   :enter (fn [ctx]
            (let [result (f)]
              (if (t/async? result)
                (p/promise [resolve]
                  (result
                   (fn []
                     (resolve ctx))))
                ctx)))})

(defn report-intor
  "Interceptor which calls cljs.test/report"
  [m]
  {:name (:type m)
   :enter (fn [ctx] (report m) ctx)})

(defn var-intors
  "Sequence of interceptors which handle a single test var."
  [test]
  (let [the-var (:var test)
        test-fn (:test (meta the-var))]
    [(report-intor {:type :begin-test-var :var the-var})
     (cljs-test-intor test-fn)
     (report-intor {:type :end-test-var :var the-var})]))

(defn ns-intors
  "Sequence of interceptors which handle a single namespace, including
  once-fixtures and each-fixtures."
  [ns {:keys [tests each-fixtures once-fixtures] :as ns-data}]
  (concat
   [(report-intor {:type :begin-test-ns :ns ns})]
   (keep (comp cljs-test-intor :before) once-fixtures)
   (->> tests
        (sort-by (comp :line :meta))
        (map var-intors)
        (mapcat (fn [var-intors]
                  (concat
                   (keep (comp cljs-test-intor :before) each-fixtures)
                   var-intors
                   (reverse (keep (comp cljs-test-intor :after) each-fixtures))))))
   (reverse (keep (comp cljs-test-intor :after) once-fixtures))
   [(report-intor {:type :end-test-ns :ns ns})]))

(defn slowdown [ms]
  {:name ::slowdown
   :enter (fn [ctx]
            (p/promise [resolve]
              (js/setTimeout (fn []
                               (resolve ctx))
                             ms)))})

(defn run-tests [tests opts]
  (binding [t/report report
            t/*current-env* (empty-env opts)]
    #_(t/run-all-tests nil (empty-env opts))
    ((:test (first tests)))
    )
  )


#_(run-tests
   @test-data/test-ns-data
   {:reporters [#(log/trace :report %)
                (legacy-reporter ::t/default)]})


(set! t/report cljs-test-report)
(set! t/*current-env* (empty-env {:reporters [#(log/trace :report %)
                                              (legacy-reporter ::t/default)]}))
(-> {}
    (intor/enqueue (interpose (slowdown 1000)
                              (apply ns-intors (first @test-data/test-ns-data))))
    intor/execute)
