(ns virtuoso.v3
  "
  "
  (:refer-clojure :exclude [future
                            pmap
                            map
                            for
                            pvalues])
  (:import
   (java.lang VirtualThread)
   (java.util.concurrent Callable
                         Future
                         ExecutorService
                         Executors)))


(set! *warn-on-reflection* true)


(alias 'cc 'clojure.core)


(defmacro with-executor
  "
  "
  [[bind] & body]
  `(with-open [~bind (Executors/newVirtualThreadPerTaskExecutor)]
     ~@body))


(defmacro future [& body]
  `(let [array#
         (object-array 2)

         thread#
         (Thread/startVirtualThread
          (^{:once true} fn* []
           (let [[result# error#]
                 (try
                   [(do ~@body) nil]
                   (catch Throwable e#
                     [nil e#]))]
             (aset array# 0 result#)
             (aset array# 1 error#))))]

     (reify

       clojure.lang.IDeref
       (deref [_]
         (.join thread#)
         (let [[result# error#] array#]
           (if error#
             (throw error#)
             result#)))

       ;; clojure.lang.IBlockingDeref
       ;; (deref [_ timeout-ms timeout-val]
       ;;   (deref-future fut timeout-ms timeout-val))

       ;; clojure.lang.IPending
       ;; (isRealized [_] (.isDone fut))

       java.util.concurrent.Future
       (get [_]
         (.join thread#)
         (aget array# 0))

       (get [_ timeout unit]
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

#_
(future 123)


(defmacro future-via
  "
  Spawn a new future using a previously open executor.
  Do not wait for the task to be completed.
  "
  {:style/indent 1}
  [[executor] & body]
  `(.submit ~executor
            ;; conveyor fn
            (reify Callable
              (call [this#]
                ~@body))))


(defn underef
  "
  A helper function that accepts a lazy sequence
  of futures and derefs items lazily one by one.
  "
  [coll]
  (lazy-seq
   (when-let [f (first coll)]
     (cons (deref f) (underef (next coll))))))


(def ^int ^:const N_WINDOW 512)

(defn nmap
  ([n f coll]
   (lazy-seq
    (when-let [items (->> coll (take n) seq)]
      (concat (underef
               (with-executor [exe]
                 (cc/mapv (fn [item]
                            (future-via [exe]
                                        (f item)))
                          items)))
              (nmap n f (drop n coll))))))

  ([n f coll & colls]
   (lazy-seq
    (let [chunks
          (cons (->> coll (take n) seq)
                (cc/map (fn [coll]
                          (->> coll (take n) seq))
                        colls))

          ok?
          (some some? chunks)]

      (when ok?
        (concat (underef
                 (with-executor [exe]
                   (apply cc/mapv
                          (fn [& args]
                            (future-via [exe]
                                        (apply f args)))
                          chunks)))
                (apply nmap
                       n
                       f
                       (drop n coll)
                       (cc/map (fn [coll]
                                 (drop n coll))
                               colls))))))))

(defn map
  ([f coll]
   (nmap N_WINDOW f coll))
  ([f coll & colls]
   (apply nmap N_WINDOW f coll colls)))




#_
(nfor n [aaa 1
         ss 2
         sdf 3]

      )


#_
(defn mapv [n f coll]
  )


(comment

  ;; -------

  (defmacro with-executor [[bind] & body]
    `(with-open [~bind (Executors/newVirtualThreadPerTaskExecutor)]
       ~@body))


  (defmacro submit [[exe] & body]
    `(.submit ~exe
              ^Callable
              (^{:once true} fn* [] ~@body)))


  (defn underef
    "
  A helper function that accepts a lazy sequence
  of futures and derefs items lazily one by one.
  "
    [coll]
    (lazy-seq
     (when-let [f (first coll)]
       (cons (deref f) (underef (next coll))))))


  (defn by-chunks [n coll]
    (partition n n [] coll))

  (defn map2 [f coll n]
    (lazy-seq
     (when-let [items (->> coll (take n) (seq))]
       (concat (underef
                (with-executor [exe]
                  (cc/mapv (fn [item]
                             (submit [exe]
                                     (f item)))
                           items)))
               (map2 f (drop n coll) n)))))


  #_
  (defn mapv2 [n f coll]
    (lazy-seq
     (when-let [items (->> coll (take n) (seq))]
       (concat (underef
                (with-executor [exe]
                  (cc/mapv (fn [item]
                             (submit [exe]
                                     (f item)))
                           items)))
               (map2 f (drop n coll) n)))))


  (defn map3 [n f coll & colls]
    (lazy-seq
     (when-let [items (->> coll (take n) (seq))]
       (concat (underef
                (with-executor [exe]
                  (cc/mapv (fn [item]
                             (submit [exe]
                                     (f item)))
                           items)))
               (map2 f (drop n coll) n)))))

  (def -r
    (map 5
         (fn [x]
           (println x)
           (* x x))
         (range 20))))
