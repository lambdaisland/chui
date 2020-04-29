;; Attempt at hooking Sente up to Pohjavirta. In hindsight not sure if this can
;; ever be made to work, since Sente has a hard requirement that is able to
;; receive a ring request map of the WS upgrade request, whereas it seems in
;; Undertow the upgrade is happened by a special HttpHandler. Can this upgrade
;; handler still do its job after we've consumed the Exchange to build up a
;; request map?
;;
;; Leaving here for future reference or in case anyone who's trying to do the
;; same stumbles upon this.

(ns taoensso.sente.server-adapters.pohjavirta
  "Sente server adaptor for Pohjavirta"
  {:author "Arne Brasseur (@plexus)"}
  (:require [taoensso.encore :as enc]
            [taoensso.sente.interfaces :as i]
            [pohjavirta.request :as req]
            [pohjavirta.websocket :as ws])
  (:import (io.undertow.websockets.core WebSockets
                                        WebSocketChannel
                                        WebSocketCallback
                                        CloseMessage)
           (io.undertow.server HttpHandler
                               HttpServerExchange)))

(set! *warn-on-reflection* true)

(extend-type WebSocketChannel
  i/IServerChan
  (sch-open?  [sch] (.isOpen sch))
  (sch-close! [sch] (.close sch))
  (sch-send!  [sch websocket? msg]
    (if (.isOpen sch)
      (let [close-after-send? (if websocket? false true)
            callback (when close-after-send?
                       (reify WebSocketCallback
                         (complete [_ _ _] (.close sch))
                         (onError [_ _ _ _] (.close sch))))]
        (if close-after-send?
          (WebSockets/sendText ^String msg sch callback)
          true))
      false)))

(deftype UndertowServerChanAdapter []
  i/IServerChanAdapter
  (ring-req->server-ch-resp [sch-adapter ring-req callbacks-map]
    (let [{:keys [on-open on-close on-msg on-error]} callbacks-map
          ws? (:websocket? ring-req)
          exchange (req/exchange ring-req)
          handler (ws/ws-handler
                   (cond-> {}
                     on-open
                     (assoc :on-open
                            (fn [{:keys [channel]}]
                              (on-open channel ws?)))
                     on-error
                     (assoc :on-error
                            (fn [{:keys [channel error]}]
                              (on-error channel ws? error)))
                     on-msg
                     (assoc :on-message
                            (fn [{:keys [channel data]}]
                              (on-msg channel ws? data)))
                     on-close
                     (assoc :on-close-message
                            (fn [{:keys [channel ^CloseMessage message]}]
                              ;; pohjavirta actually hardcodes this as
                              ;; GOING_AWAY, but passing it along anyway
                              (on-close channel ws? {:code (.getCode message)
                                                     :reason (.getReason message)})))))]
      (.handleRequest ^HttpHandler handler ^HttpServerExchange exchange)
      {:body "OK"})))

(defn get-sch-adapter [] (UndertowServerChanAdapter.))
