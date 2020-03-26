(ns lambdaisland.chui.test-info
  (:require [cljs.env :as env]))

(defmacro capture-test-data! []
  `(reset! tests
           ~(into []
                  (comp
                   (mapcat (comp vals :defs))
                   (filter :test)
                   (map (fn [var]
                          (into {:test-fn (:name var)
                                 :namespace `'~(symbol (namespace (:name var)))}
                                (map (juxt key #(list 'quote (val %)))) var))))
                  (->> @env/*compiler* :cljs.analyzer/namespaces vals))))


#_
(->> env
     :cljs.analyzer/namespaces
     vals
     (mapcat (comp vals :defs))
     (filter :test))
;; => ({:protocol-inline nil,
;;      :meta
;;      {:file "lambdaisland/chui_demo/a_test.cljs",
;;       :line 4,
;;       :column 10,
;;       :end-line 4,
;;       :end-column 17},
;;      :name lambdaisland.chui-demo.a-test/aa-test,
;;      :file "lambdaisland/chui_demo/a_test.cljs",
;;      :end-column 17,
;;      :method-params ([]),
;;      :protocol-impl nil,
;;      :arglists-meta (),
;;      :column 1,
;;      :variadic? false,
;;      :line 4,
;;      :ret-tag #{any clj-nil},
;;      :end-line 4,
;;      :max-fixed-arity 0,
;;      :fn-var true,
;;      :arglists nil,
;;      :test true})
