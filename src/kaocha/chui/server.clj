(ns kaocha.chui.server
  (:require [cognitect.transit :as transit]
            [pohjavirta.websocket :as ws]
            [pohjavirta.server :as server]
            [clojure.core.async :as async]
            [ring.middleware.params :as ring-params]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [kaocha.chui.log :as log])
  (:import (java.io ByteArrayInputStream
                    ByteArrayOutputStream)
           (java.util UUID)
           (java.time Instant)
           (io.undertow.websockets.core WebSockets)))

(defn epoch-nano
  "monotonic clock. -ish"
  []
  (System/nanoTime))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State

(defonce transit-read-handlers (atom {}))
(defonce transit-write-handlers (atom {}))

(defn initial-state []
  (let [recv-ch (async/chan 8)
        recv-mult (async/mult recv-ch)]
    {:running? false
     :recv-ch recv-ch
     :recv-mult recv-mult
     :listeners {}
     :clients {}}))

(defonce server-state (atom (initial-state)))

(defn channel->id [channel state]
  (some #(when (identical? channel (:channel %))
           %)
        (vals (:clients state))))

(defn register-tester [id recv]
  (swap! server-state
         (fn [state]
           (async/tap (:recv-mult state) recv)
           (update state :testers
                   assoc id
                   {:id id :recv recv}))))

(defn unregister-tester [id]
  (swap! server-state
         (fn [state]
           (when-let [{recv :recv} (get-in state [:testers id])]
             (async/untap (:recv-mult state) recv))
           (update state :testers dissoc id))))

(defn list-clients []
  (vals (:clients @server-state)))

(defn client [id]
  (get (:clients @server-state) id))

(defn swap-client! [cid f & args]
  (apply swap! server-state update-in [:clients cid] f args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers

(defn to-transit [value]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json {:handlers @transit-write-handlers})]
    (transit/write writer value)
    (.toString out)))

(defn from-transit [^String transit]
  (let [in (ByteArrayInputStream. (.getBytes transit))
        reader (transit/reader in :json {:handlers @transit-read-handlers})]
    (transit/read reader)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Websocket callbacks / handlers

(defmulti handle-message (fn [msg _] (:type msg)))

(defmethod handle-message :default [msg client]
  (log/debug :unhandled-message msg :client client))

(defmethod handle-message :pong [msg client]
  (log/trace :pong (:client-id client)))

(defn on-open [{:keys [channel]}]
  #_(let [id (UUID/randomUUID)]
      (swap! server-state
             update :clients
             assoc id {:id id
                       :channel channel
                       :connected (Instant/now)})))

(defn on-close [{:keys [client-id channel]}]
  (let [client-id (channel->id channel @server-state)]
    (log/trace :on-close {:client-id client-id})
    (swap! server-state
           (fn [state]
             (update state :clients dissoc client-id)))))

(defn on-message [{:keys [channel data]}]
  (let [{:keys [client-id] :as msg} (from-transit data)
        client (client client-id)
        now    (epoch-nano)]
    (log/trace :on-message {:client-id client-id :message msg})
    (handle-message msg (or client {:channel channel}))
    (swap-client! client-id assoc :last-contact now)))

(defn send! [client-id message]
  (log/trace :ws-send {:client-id client-id :message message})
  (WebSockets/sendText (to-transit message) (get-in @server-state [:clients client-id :channel]) nil))

(defmethod handle-message :kaocha.chui.client/connected [{:keys [client-info]} client]
  (log/trace :kaocha.chui.client/connected client-info)
  (swap-client!
   (:client-id client-info)
   (constantly
    (assoc client-info :channel (:channel client)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server

(defn handler []
  (ws/ws-handler {:on-open #'on-open
                  :on-message #'on-message
                  :on-close #'on-close}))

(defn running? []
  (:running? @server-state))

(defn gc-clients! []
  (swap! server-state
         update :clients
         (fn [clients]
           (let [now (epoch-nano)]
             (into {}
                   (filter (fn [[_ {:keys [client-id channel last-contact]}]]
                             (when (and channel (.isOpen channel))
                               ;; We ping/pong every 5 seconds, 60 seconds after
                               ;; the last contact we assume the client has gone
                               ;; away
                               (if (< (+ last-contact 60e9) now)
                                 (do
                                   (log/debug :client-timeout {:client-id client-id
                                                               :seconds-since-last-contact
                                                               (double (/ last-contact 1e9))})
                                   (.close channel)
                                   false)
                                 true))))
                   clients)))))

(defn broadcast! [message]
  (run! #(try
           (send! % message)
           (catch Throwable e))
        (keys (:clients @server-state))))

(defn keepalive-thread []
  (let [stop? (promise)]
    (.start
     (Thread.
      (fn []
        (while (not (realized? stop?))
          (Thread/sleep 5000)
          (broadcast! {:type :ping})
          (gc-clients!)))))
    stop?))

(defn start! [opts]
  (log/config :pohjavirta opts)
  (when-not (running?)
    (let [server (server/create (handler))]
      (swap! server-state
             assoc
             :running? true
             :undertow server
             :opts opts
             :stop-keepalive (keepalive-thread))
      (server/start server))))

(defn stop! []
  (log/config :stopping-server {})
  (swap! server-state
         (fn [{:keys [running? undertow stop-keepalive] :as state}]
           (deliver stop-keepalive true)
           (doseq [{:keys [channel]} (list-clients)]
             (.close channel))
           (when running?
             (server/stop undertow)
             (-> state
                 (dissoc :undertow :opts :clients)
                 (assoc :running? false))))))

(comment
  (start! {})
  (stop!)
  (broadcast! {:type :fetch-test-data})


  (server/start s)

  (server/stop s)
  (swap! server-state dissoc :clients)
  @server-state
  (map :last-contact (list-clients))
  (map #(.isOpen %) (keep :channel (list-clients)))

  events

  (gc-clients!)

  (def events (atom []))

  (def handlers
    {:on-open    (fn [{:keys [channel] :as e}]
                   (swap! events conj [:open e]))
     :on-message (fn [{:keys [channel data] :as e}]
                   (swap! events conj [:message e]))
     :on-close   (fn [{:keys [channel ws-channel] :as e}]
                   (swap! events conj [:close e]))
     :on-error   (fn [{:keys [channel error] :as e}]
                   (swap! events conj [:error e]))})

  (def handler (ws/ws-handler handlers))
  ;; => #object[io.undertow.websockets.WebSocketProtocolHandshakeHandler 0x491f1684 "io.undertow.websockets.WebSocketProtocolHandshakeHandler@491f1684"]

  (def server  (server/create handler))
  ;; => #object[io.undertow.Undertow 0x4bf259ca "io.undertow.Undertow@4bf259ca"]

  (server/start server)
  ;;=> nil

  (ancestors (class (:channel (last (first @events))))))
  ;; => #{java.lang.Object org.xnio.channels.Configurable java.io.Closeable
  ;;      io.undertow.websockets.core.protocol.version07.WebSocket07Channel
  ;;      java.lang.AutoCloseable org.xnio.channels.BoundChannel
  ;;      io.undertow.websockets.core.WebSocketChannel
  ;;      org.xnio.channels.CloseableChannel
  ;;      io.undertow.server.protocol.framed.AbstractFramedChannel
  ;;      java.nio.channels.Channel java.nio.channels.InterruptibleChannel
  ;;      org.xnio.channels.ConnectedChannel}
