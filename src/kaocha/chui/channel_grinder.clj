(ns kaocha.chui.channel-grinder
  "Core.async based coffee grinder"
  (:require [clojure.core.async :as async]
            [kaocha.chui.log :as log]))

(defn execute [chan
               {:keys [timeout handlers result init]
                :or {init {}
                     timeout 15000
                     result :done?}}]
  (let [poll #(async/alt!!
                chan ([msg _] msg)
                (async/timeout timeout) ([_ _] :timeout))]
    (loop [message (poll)
           ctx init]
      (log/trace :message message :ctx ctx)

      (if (= :timeout message)
        (if-let [timeout-handler (get handlers :timeout #(throw (ex-info "Timeout" {::ctx %})))]
          (timeout-handler ctx)
          :timeout)

        (if-let [handler (get handlers (:type message))]
          (let [ctx (handler message ctx)]
            #_(prn "    CTX" ctx)
            (if-let [result (result ctx)]
              result
              (recur (poll) ctx)))
          (recur (poll) ctx))))))
