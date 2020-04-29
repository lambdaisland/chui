(ns lambdaisland.chui.runner
  (:require [cljs.test :as t]
            [clojure.string :as str]
            [kitchen-async.promise :as p]
            [lambdaisland.chui.interceptor :as intor]
            [lambdaisland.chui.test-data :as test-data]
            [lambdaisland.glogi :as log]
            [goog.async.nextTick]))

(defonce state (atom {:runs []
                      :ctx-promise nil
                      :selection nil}))

(defn current-run []
  (last (:runs @state)))

(defn update-run [f & args]
  (swap! state
         update :runs
         (fn [runs]
           (apply update runs (dec (count runs)) f args))))

(defn update-run-ns [f & args]
  (update-run
   (fn [run]
     (apply update-in run [:nss (dec (count (:nss run)))] f args))))

(defn update-run-var [f & args]
  (update-run-ns
   (fn [ns]
     (apply update-in ns [:vars (dec (count (:vars ns)))] f args))))

(defn get-and-clear-report-counters []
  (let [counters (:report-counters t/*current-env*)]
    (t/update-current-env! [:report-counters] (constantly {:test 0 :pass 0 :fail 0 :error 0}))
    counters))

(defn new-test-run! [m]
  (dec (count (swap! state update :runs conj m))))

(defn cljs-test-intor
  "Turn a function which may return a cljs.test IAsyncTest into a promise-based interceptor.

  An IAsyncTest is a special type of object which is callable. It takes a single
  argument: a continuation callback, which is a zero-arity function. In effect
  it is like a promise which does not yield a value, but simply signals that
  some process has completed.

  IAsyncTest values are created using the `cljs.test/async` macro, which may be
  used in tests (deftest) and fixtures to implement asynchrony. "
  [name f]
  {:name :cljs-test-intor
   :test name
   :enter (fn [ctx]
            (let [result (f)]
              (cond
                (t/async? result)
                (p/promise [resolve]
                  (result
                   (fn []
                     (resolve ctx))))
                (instance? js/Promise result)
                (p/promise [resolve]
                  (.then result
                         (fn [_]
                           (resolve ctx))
                         (fn [error]
                           (resolve (assoc ctx :error error)))))
                :else
                ctx)))})

(defn report-intor
  "Interceptor which calls cljs.test/report"
  [m]
  {:name (:type m)
   :enter (fn [ctx] (t/report m) ctx)})

(defn var-intors
  "Sequence of interceptors which handle a single test var."
  [test]
  (let [the-var (:var test)
        test-fn (:test test)]
    [(report-intor {:type :begin-test-var :var the-var})
     {:name :begint-var-update-env
      :enter (fn [ctx]
               (t/update-current-env! [:testing-vars] conj the-var)
               (t/update-current-env! [:report-counters :test] inc)
               (update-run-ns update :vars conj (assoc test
                                                       :start (js/Date.)
                                                       :assertions []
                                                       :done? false))
               ctx)}
     (cljs-test-intor (:name test) test-fn)
     {:name :end-var-update-env
      :enter (fn [ctx]
               (t/update-current-env! [:testing-vars] rest)
               (update-run-var assoc
                               :end (js/Date.)
                               :done? true)
               ctx)}
     (report-intor {:type :end-test-var :var the-var})]))

(defn fixture-intors [ns stage type fixtures]
  (let [fix (keep stage fixtures)]
    (cond-> (map #(cljs-test-intor (keyword (str ns) (str (name stage) "-" (name type))) %) fix)
      (= :after stage)
      reverse)))

(defn ns-intors
  "Sequence of interceptors which handle a single namespace, including
  once-fixtures and each-fixtures."
  [ns {:keys [meta tests each-fixtures once-fixtures] :as ns-data}]
  (when-let [tests (seq tests)]
    (concat
     [(report-intor {:type :begin-test-ns :ns ns})
      {:name :begin-ns-update-run
       :enter (fn [ctx]
                (update-run update :nss
                            conj {:ns ns
                                  :start (js/Date.)
                                  :done? false
                                  :vars []})
                ctx)}]
     (fixture-intors ns :before :once once-fixtures)
     (->> tests
          (sort-by (comp :line :meta))
          (map var-intors)
          (mapcat (fn [var-intors]
                    (concat
                     (fixture-intors ns :before :each once-fixtures)
                     var-intors
                     (fixture-intors ns :after :each once-fixtures)))))
     (fixture-intors ns :after :once once-fixtures)
     {:name :end-ns-update-run
      :enter (fn [ctx]
               (update-run update :nss
                           assoc
                           :end (js/Date.)
                           :done? true)
               ctx)}
     [(report-intor {:type :end-test-ns :ns ns})])))

(def log-error-intor
  {:name ::log-error
   :error (fn [ctx error]
            (t/report
             {:type :error
              :message "Uncaught exception, not in assertion."
              :expected nil
              :actual (ex-cause error)})
            (dissoc ctx :error))})

;; for debugging / visualizing progress
(defn slowdown-intor [ms]
  {:name ::slowdown
   :enter (fn [ctx]
            (p/promise [resolve]
              (js/setTimeout (fn []
                               (resolve ctx))
                             ms)))})

(defn next-tick-intor
  "Interceptor which continues on the next tick, used to allow the UI to update."
  []
  {:name ::next-tick
   :enter (fn [ctx]
            (p/promise [resolve]
              (goog.async.nextTick
               (fn []
                 (resolve ctx)))))})

;; cljs.test's version of this is utterly broken. This version is not great but
;; at least it kind of works in both Firefox and Chrome. To do this properly
;; we'll have to use something like stacktrace.js
(defn file-and-line []
  (let [frame (-> (js/Error.)
                  .-stack
                  (str/split #"\n")
                  (->> (drop-while #(not (str/includes? % "do_report"))))
                  (nth 1)
                  (str/split #":"))
        line-col (drop (- (count frame) 2) frame)
        file (str/join ":" (take (- (count frame) 2) frame))]
    {:file file
     :line (js/parseInt (re-find #"\d+" (first line-col)) 10)
     :column (js/parseInt (re-find #"\d+" (second line-col)) 10)}))

(defmethod t/report [::default :fail] [m]
  (update-run-var update :assertions conj
                  (merge
                   m
                   (file-and-line)
                   (select-keys t/*current-env* [:testing-contexts :testing-vars]))))

(defmethod t/report [::default :error] [m]
  (update-run-var update :assertions conj
                  (merge
                   m
                   (select-keys t/*current-env* [:testing-contexts :testing-vars]))))

(defmethod t/report [::default :pass] [m]
  (update-run-var update :assertions conj
                  (merge
                   m
                   (file-and-line)
                   (select-keys t/*current-env* [:testing-contexts :testing-vars]))
                  ))

(defn var-summary [{:keys [assertions]}]
  (assoc (frequencies (map :type assertions)) :test 1))

(defn ns-summary [{:keys [vars]}]
  (merge
   {:tests 0 :pass 0 :fail 0 :error 0}
   (frequencies
    (flatten
     (for [{:keys [assertions]} vars]
       [:tests
        (for [{:keys [type]} assertions]
          type)])))))

(defn run-summary [{:keys [nss]}]
  (apply merge-with +
         {:tests 0 :pass 0 :fail 0 :error 0}
         (map ns-summary nss)))

(defn error? [{:keys [error]}]
  (pos-int? error))

(defn fail? [{:keys [fail error]}]
  (or (pos-int? fail)
      (pos-int? error)))

(def pass? (complement fail?))

(defn run-tests
  ([]
   (let [tests @test-data/test-ns-data]
     (run-tests
      (if-let [selected (seq (:selected @state))]
        (select-keys tests selected)
        (into {}
              (remove (comp :test/skip :meta val))
              tests)))))
  ([tests]
   (let [terminate? (atom false)]
     (new-test-run! {:id (random-uuid)
                     :test-count (apply + (map (comp  count :tests val) tests))
                     :terminate! #(reset! terminate? true)
                     :nss []
                     :ctx {}
                     :done? false
                     :start (js/Date.)})
     (set! t/*current-env* (t/empty-env ::default))
     (let [ctx-promise (-> {::intor/terminate? terminate?
                            ::intor/on-context #(update-run assoc :ctx %)}
                           (intor/enqueue [log-error-intor])
                           (intor/enqueue #_(interpose (slowdown-intor 300))
                                          (interpose (next-tick-intor)
                                                     (mapcat #(apply ns-intors %) tests)))
                           intor/execute)]
       (update-run assoc :donep ctx-promise)
       (p/let [ctx ctx-promise]
         (update-run assoc
                     :ctx ctx
                     :end (js/Date.)
                     :done? true))))))

(defn terminate!
  ([]
   (terminate! nil))
  ([callback]
   (when-let [run (current-run)]
     (when-let [donep (and callback (:donep run))]
       (p/let [ctx donep]
         (when-not (:done? run)
           (update-run assoc :terminated? true))
         (callback ctx)))
     ((:terminate! run)))))

(comment
  (defn legacy-reporter [reporter]
    (fn [m]
      ((get-method cljs-test-report [reporter (:type m)]) m)))

  (defn report [m]
    (doseq [f (:reporters (t/get-current-env))]
      (f m)))

  (get @test-data/test-ns-data 'pitch.app.block.table.layout-test)
  )
