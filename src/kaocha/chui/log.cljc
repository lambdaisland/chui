(ns kaocha.chui.log
  #?(:cljs (:require-macros [kaocha.chui.log]))
  (:require [lambdaisland.glogi :as glogi]
            #?(:clj [io.pedestal.log :as pedestal])))

#?(:clj
   (do

     (defmacro case-platform [& {:keys [cljs clj]}]
       `(if (:ns ~'&env) ~cljs ~clj))

     (defmacro error [& keyvals]
       (case-platform :clj  (#'pedestal/log-expr &form :error keyvals)
                      :cljs (#'glogi/log-expr &form :error ~@keyvals)))

     (defmacro warn [& keyvals]
       (case-platform :clj  (#'pedestal/log-expr &form :warn keyvals)
                      :cljs (#'glogi/log-expr &form :warn ~@keyvals)))

     (defmacro info [& keyvals]
       (case-platform :clj  (#'pedestal/log-expr &form :debug keyvals)
                      :cljs (#'glogi/log-expr &form :info ~@keyvals)))

     (defmacro config [& keyvals]
       (case-platform :clj  (#'pedestal/log-expr &form :debug keyvals)
                      :cljs (#'glogi/log-expr &form :config ~@keyvals)))

     (defmacro debug [& keyvals]
       (case-platform :clj  (#'pedestal/log-expr &form :debug keyvals)
                      :cljs (#'glogi/log-expr &form :debug ~@keyvals)))

     (defmacro trace [& keyvals]
       (case-platform :clj  (#'pedestal/log-expr &form :trace keyvals)
                      :cljs (#'glogi/log-expr &form :trace ~@keyvals)))

     (defmacro finest [& keyvals]
       (case-platform :clj  (#'pedestal/log-expr &form :trace keyvals)
                      :cljs (#'glogi/log-expr &form :finest ~@keyvals)))

     (defmacro spy [expr]
       (case-platform :clj `(pedestal/spy ~expr)
                      :cljs `(glogi/spy ~expr)))))
