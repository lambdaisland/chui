(ns lambdaisland.chui.interceptor
  "Implementation of the interceptor pattern with the following properties

  - Uses JS promises for asynchrony
  - Allows enqueuing more interceptors in a :leave or :error stage (this will
    effectively switch back to the :enter stage of processing until the queue is
    empty again)
  - Takes an optional :on-context callback, for progress updates
  - Takes an optional :terminate? atom, for signaling that the process should be
    terminated as soon as possible"
  (:require [kitchen-async.promise :as p]
            [lambdaisland.chui.util :as util]
            [lambdaisland.glogi :as log]))

(defn- throwable->ex-info [t interceptor stage]
  (log/warn :error-in-interceptor (assoc interceptor :stage stage) :exception t)
  (ex-info (str "Exception in interceptor " (:name interceptor) " during the " stage " stage.")
           (merge
            {:stage stage
             :interceptor (:name interceptor)
             :exception-type (keyword (pr-str (type t)))
             :exception t}
            (ex-data t))
           t))

(declare execute*)

(defn- try-stage
  "Try running a specific stage of the given interceptor.

  Will catch exceptions and switch the context over to error handling by
  removing the `::queue` and adding an `::error` key."
  [stage interceptor ctx & args]
  (log/fine :try-stage {:stage stage :interceptor interceptor})
  (if-let [f (get interceptor stage)]
    (try
      (let [obj (apply f ctx args)]
        (if (util/thenable? obj)
          (-> obj
              (p/then
               execute*
               (fn [err]
                 (execute* (assoc ctx ::error (throwable->ex-info err interceptor stage))))))
          (execute* obj)))
      (catch :default t
        (-> ctx
            (assoc ::error (throwable->ex-info t interceptor stage))
            execute*)))
    (execute* ctx)))

(defn into-queue
  "Add elements to a queue, setting up a new queue if no queue was provided."
  ([xs]
   (into-queue nil xs))
  ([q xs]
   ((fnil into #queue []) q xs)))

(defn enter-1
  "Invoke the `:enter` stage of the next interceptor.

  Pop the next interceptor off the queue, push it onto the stack, and run its
  `:enter` stage if it has one. "
  [{::keys [queue stack] :as ctx}]
  (let [interceptor (peek queue)
        new-queue   (pop queue)
        new-stack   (conj stack interceptor)
        new-context (assoc ctx ::queue new-queue ::stack new-stack)]
    (try-stage :enter interceptor new-context)))

(defn leave-1
  "Invoke the `:leave` stage of the next interceptor.

  Pop the next interceptor off the stack, and run its `:leave` stage if it has
  one."
  [{::keys [stack] :as ctx}]
  (let [interceptor (peek stack)
        new-stack   (pop stack)
        new-context (assoc ctx ::stack new-stack)]
    (try-stage :leave interceptor new-context)))

(defn error-1
  "Invoke the `:error` stage of the next interceptor.

  Pop the next interceptor off the stack, and run its `:enter` stage if it has
  one."
  [{::keys [stack error] :as ctx}]
  (let [interceptor (peek stack)
        new-stack   (pop stack)
        new-context (assoc ctx ::stack new-stack)]
    (try-stage :error interceptor new-context error)))


(defn execute*
  "Modified interceptor chain, only processes the enter chain. Takes a context map
  with a ::queue of interceptors to be executed. Other special values that can
  be passed in as part of the context:

  - `::on-context` a callback that gets called at every iteration with the new
    context map. Useful for keeping track of progress. Note that `::on-context`
    gets called on the *start* of every iteration. To get the final context see
    `::resolve`/`::reject`.
  - `::on-error` error handler, receives the context and the error, must return
    a context
  - `::terminate?` an atom which, when set to true, will short circuit the
    process at the next possible occasion
  - `::resolve` function that gets called with the final context map"
  [{::keys [queue stack error on-context on-error terminate? resolve] :as ctx}]
  (log/trace :execute ctx)

  (when on-context
    (on-context ctx))

  (cond
    (and terminate? @terminate?) (resolve (assoc ctx ::terminated? true))
    (and error on-error)         (recur (dissoc (on-error ctx error) ::error))
    ;; (and error (seq stack))      (error-1 ctx)
    (seq queue)                  (enter-1 ctx)
    ;; (seq stack)                  (leave-1 ctx)
    :else                        (resolve ctx)))

(defn execute [ctx]
  (p/promise [resolve _]
    (execute* (assoc ctx ::resolve resolve))))

(defn enqueue
  "Enqueue interceptors.

  Add interceptors to the context's FIFO queue."
  [ctx interceptors]
  (update-in ctx [::queue] into-queue interceptors))

(defn ctx-summary
  "Take a context map, but rewrite the queue, stack and error to be more concise
  for easy inspection."
  [{::keys [queue stack error] :as ctx}]
  (cond-> (dissoc ctx ::queue ::stack ::error)
    (some? queue)
    (assoc :queue (into-queue (map :name queue)))
    (some? stack)
    (assoc :stack (map :name stack))
    (some? error)
    (assoc :error (dissoc (ex-data error) :exception))))
