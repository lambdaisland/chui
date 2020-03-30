(ns lambdaisland.chui.test-data
  (:require [cljs.env :as env]))

(defn- var-ns [var]
  (symbol (namespace (:name var))))

(defn- var-name [var]
  (symbol (name (:name var))))

(defn- test-var-info [var]
  {:name `'~(:name var)
   :test `(:test (meta (var ~(:name var))))
   :ns   `'~(var-ns var)
   :var  `(var ~(:name var))
   :meta `'~(:meta var)})

(defn- all-cljs-vars []
  (->> @env/*compiler*
       :cljs.analyzer/namespaces
       vals
       (mapcat (comp vals :defs))))

(defn- test-data []
  (reduce
   (fn [m v]
     (cond
       (:test v)
       (update-in m [`'~(var-ns v) :tests] (fnil conj []) (test-var-info v))

       (=  'cljs-test-once-fixtures (var-name v))
       (assoc-in m [`'~(var-ns v) :once-fixtures] (:name v))

       (=  'cljs-test-each-fixtures (var-name v))
       (assoc-in m [`'~(var-ns v) :each-fixtures] (:name v))

       :else
       m))
   {}
   (all-cljs-vars)))

(defmacro capture-test-data! []
  `(do
     (reset! test-ns-data ~(doto (test-data) prn))
     (lambdaisland.glogi/debug :lambdaisland.chui/capture-test-data! @test-ns-data)))


#_
(->> env
     :cljs.analyzer/namespaces
     vals
     (mapcat (comp vals :defs))
     (filter :test))
;; => ({:protocol-inline nil
;;      :meta
;;      {:file "lambdaisland/chui_demo/a_test.cljs"
;;       :line 4
;;       :column 10
;;       :end-line 4
;;       :end-column 17}
;;      :name lambdaisland.chui-demo.a-test/aa-test
;;      :file "lambdaisland/chui_demo/a_test.cljs"
;;      :end-column 17
;;      :method-params ([])
;;      :protocol-impl nil
;;      :arglists-meta ()
;;      :column 1
;;      :variadic? false
;;      :line 4
;;      :ret-tag #{any clj-nil}
;;      :end-line 4
;;      :max-fixed-arity 0
;;      :fn-var true
;;      :arglists nil
;;      :test true})
