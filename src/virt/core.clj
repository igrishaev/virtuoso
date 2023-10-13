(ns virt.core
  (:refer-clojure :exclude [future pmap])
  (:import
   clojure.lang.RT
   java.util.concurrent.Callable
   java.util.concurrent.Executors
   java.util.concurrent.ExecutorService))


(defmacro with-executor [[bind] & body]
  `(with-open [~bind (Executors/newVirtualThreadPerTaskExecutor)]
     ~@body))


(defmacro with-future
  [executor & body]
  `(.submit ~executor
            (reify Callable
              (call [this#]
                ~@body))))



(defmacro future [& body]
  `(with-executor [executor#]
     (with-future executor#
       ~@body)))


#_
(with-executor [exe]
  (with-future exe
    (+ 1 2 3)))


#_
(let [f (binding-conveyor-fn f)
      fut (.submit clojure.lang.Agent/soloExecutor ^Callable f)]
  (reify
    clojure.lang.IDeref
    (deref [_] (deref-future fut))
    clojure.lang.IBlockingDeref
    (deref
        [_ timeout-ms timeout-val]
      (deref-future fut timeout-ms timeout-val))
    clojure.lang.IPending
    (isRealized [_] (.isDone fut))
    java.util.concurrent.Future
    (get [_] (.get fut))
    (get [_ timeout unit] (.get fut timeout unit))
    (isCancelled [_] (.isCancelled fut))
    (isDone [_] (.isDone fut))
    (cancel [_ interrupt?] (.cancel fut interrupt?))))


(defmacro thread [& body]
  `(.start (Thread/ofVirtual)
           (reify Runnable
             (run [this#]
               ~@body))))


(defn pmap

  ([func coll]
   (with-executor [executor]
     (let [iter (RT/iter coll)]
       (loop [acc! (transient [])]
         (if (.hasNext iter)
           (let [x (.next iter)
                 f (with-future executor
                     (func x))]
             (recur (conj! acc! f)))
           (persistent! acc!))))))

  ([func coll & colls]
   #_
   (let [iters [(RT/iter coll)]]
     (loop [acc! (transient [])]
       (if (.hasNext iter)
         (let [x (.next iter)]
           (recur (conj! acc! (future (func x)))))
         (persistent! acc!))))

   )

  )
