(ns kaocha.chui.channel-grinder
  "Core.async based coffee grinder"
  (:require [clojure.core.async :as async]
            [kaocha.chui.log :as log]))

(defn execute [chan
               {:keys [timeout handlers result init]
                :or {init {}
                     timeout 15000}}]
  (let [poll #(async/alt!!
                chan ([msg _] msg)
                (async/timeout timeout) ([_ _] :timeout))]
    (loop [message (poll)
           state init]
      (log/trace (:type message) message :state state)

      (if (= :timeout message)
        (if-let [timeout-handler (get handlers :timeout)]
          (timeout-handler state)
          :timeout)

        (if-let [handler (get handlers (:type message))]
          (let [state (handler message state)]
            #_(prn "    STATE" state)
            (if-let [result (result state)]
              result
              (recur (poll) state)))
          (recur (poll) state))))))
