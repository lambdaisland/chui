(ns kaocha.chui
  (:require [clojure.java.browse :as browse]
            [shadow.cljs.devtools.server :as shadow-server]
            [shadow.cljs.devtools.server.runtime :as shadow-server-runtime]
            [shadow.cljs.devtools.api :as shadow-api]
            [kaocha.chui.server :as server]
            [kaocha.chui.log :as log]))

;; (defn shadow-browser-url
;;   "Tries to guess the URL to open in the browser. Only works with :dev-http.
;;   Set :chui/browser-url explicitly if this isn't guessing correctly."
;;   [build-config]
;;   (let [dev-server (some-> (shadow-server-runtime/get-instance)
;;                            :dev-http
;;                            deref
;;                            :servers
;;                            first)
;;         origin ((some-fn :https-url :http-url) dev-server)]
;;     (str origin "/" (:test-dir build-config) "/index.html")))

(defn compile-shadow [testable]
  (log/info :compile-shadow (select-keys testable [:kaocha.testable/id :shadow/build-id]))
  (shadow-server/start!)
  (let [build-state (shadow-api/compile! (:shadow/build-id testable) {})
        build-config (:shadow.build/config build-state)]

    testable))

(defn launch-browser [{:chui/keys [browser-url open-url?]}]
  (when (if (= :auto open-url?)
          (do
            (server/gc-clients!)
            (empty? (server/list-clients)))
          open-url?)
    (browse/browse-url browser-url)))

(defn all-connected-clients [testable]
  (server/gc-clients!)
  (loop [clients (server/list-clients)
         retries 0
         sleep 2]
    (if (seq clients)
      (map :client-id clients)
      (if (< retries 5)
        (do
          (log/warn :no-connected-clients {:chui/browser-url (:chui/browser-url testable)
                                           :retry-in (str sleep " sec")})
          (Thread/sleep (* sleep 1000))
          (recur (server/list-clients)
                 (inc retries)
                 (* 2 sleep)))
        (throw (ex-info "No clients connected, giving up."
                        {::all-connected-clients :no-connected-clients}))))))
