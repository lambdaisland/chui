(ns lambdaisland.chui.websocket
  "Cross-platform websocket wrapper, borrowed from Figwheel.

  Relies on goog.net.WebSocket (https://google.github.io/closure-library/api/goog.net.WebSocket.html)"
  (:require [goog.object :as gobj]
            [goog.storage.mechanism.mechanismfactory :as storage-factory]
            [goog.Uri :as guri]
            [goog.string :as gstring]
            [goog.net.jsloader :as loader]
            [goog.net.XhrIo :as xhrio]
            [goog.log :as glog]
            [goog.array :as garray]
            [goog.json :as gjson]
            [goog.html.legacyconversions :as conv]
            [goog.userAgent.product :as product])
  (:import [goog.net WebSocket]
           [goog.debug Console]
           [goog.Uri QueryData]
           [goog Promise]
           [goog.storage.mechanism HTML5SessionStorage]))

(def host-env
  (cond
    (not (nil? goog/nodeGlobalRequire)) :node
    (not (nil? goog/global.document)) :html
    (and (exists? goog/global.navigator)
         (= goog/global.navigator.product "ReactNative"))
    :react-native
    (and
     (nil? goog/global.document)
     (exists? js/self)
     (exists? (.-importScripts js/self)))
    :worker))

(def event-types
  {;; OPENED Fired when the WebSocket connection has been established.
   :open  goog.net.WebSocket.EventType.OPENED
   ;; CLOSED Fired when an attempt to open the WebSocket fails or there is a connection failure after a successful connection has been established.
   :close  goog.net.WebSocket.EventType.CLOSED
   ;; ERROR Fired when the WebSocket encounters an error.
   :error   goog.net.WebSocket.EventType.ERROR
   ;; MESSAGE Fired when a new message arrives from the WebSocket.
   :message goog.net.WebSocket.EventType.MESSAGE})

(defn get-websocket-class []
  (or
   (gobj/get goog.global "WebSocket")
   (gobj/get goog.global "FIGWHEEL_WEBSOCKET_CLASS")
   (and (= host-env :node)
        (try (js/require "ws")
             (catch js/Error e
               (js/console.log "NODE_WS_NOT_FOUND")
               nil)))
   (and (= host-env :worker)
        (gobj/get js/self "WebSocket"))))

(defn ensure-websocket [thunk]
  (if (gobj/get goog.global "WebSocket")
    (thunk)
    (when-let [websocket-class (get-websocket-class)]
      (do
        (gobj/set goog.global "WebSocket" websocket-class)
        (let [result (thunk)]
          (gobj/set goog.global "WebSocket" nil)
          result)))))

(defn make-websocket ^goog.net.WebSocket
  ([]
   (make-websocket true))
  ([reconnect?]
   (ensure-websocket #(goog.net.WebSocket. reconnect?))))

(defn register-handlers* [^goog.net.WebSocket ws handler-map]
  (doseq [[type handler-fn] handler-map]
    ;; TODO addEventListener is deprecated, can we swap for (.listen ...)
    (assert (get event-types type) type)
    (.addEventListener ws (get event-types type) handler-fn)))

(defn register-handlers [^goog.net.WebSocket ws handler-map]
  (ensure-websocket #(register-handlers* ws handler-map)))

(defn open!* [^goog.net.WebSocket ws url]
  (.open ws url))

(defn open! [^goog.net.WebSocket ws url]
  (ensure-websocket #(open! ws url)))

(defn connect!
  ([url handler-map]
   (connect! url handler-map true))
  ([url handler-map reconnect?]
   (ensure-websocket
    #(let [ws (goog.net.WebSocket. reconnect?)]
       (register-handlers* ws handler-map)
       (open!* ws url)
       ws))))

(defn send! [^goog.net.WebSocket ws msg]
  (ensure-websocket #(.send ws msg)))

(defn open? [^goog.net.WebSocket ws]
  (ensure-websocket #(.isOpen ws)))

(defn close! [^goog.net.WebSocket ws]
  (.close ws))

(defn message-data [event]
  (gobj/get event "message"))
