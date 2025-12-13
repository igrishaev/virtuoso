
(time
 (do
   (mapv deref
         (map
          (fn [x]
            (future
              (cheshire/get "https://github.com/seductiveapps/largeJSON/raw/master/100mb.json")))
          (range 0 10)))
   :end))

;; "https://microsoftedge.github.io/Demos/json-dummy-data/1MB.json

;; "https://raw.githubusercontent.com/json-iterator/test-data/refs/heads/master/large-file.json"

(time (->> (clojure.core/range 0 100)
           (clojure.core/map
            (fn [x]
              (clojure.core/future
                (try
                  (clj-http.client/get "https://microsoftedge.github.io/Demos/json-dummy-data/1MB.json")
                  x
                  (catch Throwable e
                    :error)
                  (finally
                    (println x "DONE"))))))
           (clojure.core/doall)
           (clojure.core/mapv deref)))


(time (->> (clojure.core/range 0 100)
           (map
            (fn [x]
              (try
                (clj-http.client/get "https://microsoftedge.github.io/Demos/json-dummy-data/1MB.json")
                x
                (catch Throwable e
                  :error)
                (finally
                  (println x "DONE")))))
           (doall)))

(defmacro future [& body]
  `(let [array#
         (object-array 2)

         thread#
         (Thread/startVirtualThread
          (binding-conveyor-fn
           (^{:once true} fn* []
            (let [[result# error#]
                  (try
                    [(do ~@body) nil]
                    (catch Throwable e#
                      [nil e#]))]
              (aset array# 0 result#)
              (aset array# 1 error#)))))]

     (reify

       clojure.lang.IDeref
       (deref [_#]
         (.join thread#)
         (let [[result# error#] array#]
           (if error#
             (throw error#)
             result#)))

       #_
       clojure.lang.IBlockingDeref
       #_
       (deref [_ timeout-ms# timeout-val#]
         (.join thread# timeout-val#)
         (let [[result# error#] array#]
           (if error#
             (throw error#)
             result#)))

       clojure.lang.IPending
       (isRealized [_]
         (.isAlive thread#))

       java.util.concurrent.Future
       (get [this#]
         (deref this#))

       #_
       (get [_ timeout unit]
         (.join thread# timeout-val#)
         ;; convert
         #_
         (.get fut timeout unit))

       (isCancelled [_]
         (.isInterrupted thread#))

       (isDone [_]
         (not (.isAlive thread#)))

       (cancel [_ interrupt?]
         (.interrupt thread#))))
  )


(defmacro future
  "
  TODO
  "
  ^CompletableFuture [& body]
  `(let [future# (new CompletableFuture)]
     (Thread/startVirtualThread
      (binding-conveyor-fn
       (^{:once true} fn* []
        (let [[result# e#]
              (try
                [(do ~@body) nil]
                (catch Throwable e#
                  [nil e#]))]
          (if e#
            (.completeExceptionally future# e#)
            (.complete future# result#))))))
     future#))
