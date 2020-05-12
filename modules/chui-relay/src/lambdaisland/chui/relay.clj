(ns lambdaisland.chui.relay
  (:require [cognitect.transit :as transit]
            [pohjavirta.websocket :as ws]
            [pohjavirta.server :as server]
            [clojure.core.async :as async]
            [ring.middleware.params :as ring-params]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [io.pedestal.log :as log]
            [clojure.string :as str])
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
  {:running? false
   :clients {}})

(defonce server-state (atom (initial-state)))

(defn channel->id [channel state]
  (some #(when (identical? channel (:channel %))
           %)
        (vals (:clients state))))

(defn list-clients []
  (vals (:clients @server-state)))

(defn client [id]
  (get (:clients @server-state) id))

(defn swap-client! [cid f & args]
  (apply swap! server-state update-in [:clients cid] f args))

(defn tap-client [cid chan]
  (async/tap (:mult (client cid)) chan))

(defn untap-client [cid chan]
  (async/untap (:mult (client cid)) chan))

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
  (log/debug :put-message msg)
  (when-let [chan (:chan client)]
    (async/>!! chan msg)))

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

(defn humanized-id [client clients]
  (let [platform-name (str/lower-case (first (str/split (:platform client) #" ")))
        existing-ids (set (map :humanized-id clients))]
    (prn existing-ids)
    (->> (next (range))
         (map #(keyword (str platform-name "-" %)))
         (remove existing-ids)
         first)))

(defmethod handle-message :connected [{:keys [client-info]} client]
  (log/trace :connected client-info)
  (let [client (merge client client-info)
        client (if-not (and (:mult client) (:chan client))
                 (let [chan (async/chan 8)
                       mult (async/mult chan)]
                   (assoc client
                          :chan chan
                          :mult mult))
                 client)]
    (swap! server-state
           update :clients
           (fn [clients]
             (assoc clients
                    (:client-id client-info)
                    (assoc client :humanized-id (humanized-id client (vals clients))))))))

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
  (log/info :pohjavirta/config opts)
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
  (log/info :stopping-server {})
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

  @server-state


  (gc-clients!)

  )
