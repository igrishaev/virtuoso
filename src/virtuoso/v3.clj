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

#_
(future 123)


#_
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
      (let [futs
            (cc/mapv (fn [item]
                       (future (f item)))
                     items)]
        (concat (underef futs)
                (nmap n f (drop n coll)))))))

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
        (let [futs
              (apply cc/mapv
                     (fn [& args]
                       (future (apply f args)))
                     chunks)]
          (concat (underef futs)
                  (apply nmap
                         n
                         f
                         (drop n coll)
                         (cc/map (fn [coll]
                                   (drop n coll))
                                 colls)))))))))

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


(defn nmap
  ([n f coll]
   (loop [coll coll
          result []]
     (let [chunk (seq (take n coll))]
       (if (nil? chunk)
         result
         (let [futs
               (with-executor [exe]
                 (cc/mapv
                  (fn [item]
                    (future-via [exe]
                      (f item)))
                  chunk))]
           (recur (drop n coll)
                  (into result futs))))))

   #_
   (lazy-seq
    (when-let [items (->> coll (take n) seq)]
      (let [futs
            (with-executor [exe]
              (cc/mapv (fn [item]
                         (future-via [exe]
                           (f item)))
                       items))]
        (concat futs
                (nmap n f (drop n coll)))))))

  ([n f coll & colls]
   (loop [colls (cons coll colls)
          result []]
     (let [chunks
           (cc/for [coll colls]
             (seq (take n coll)))]
       (if (some nil? chunks)
         result
         (let [futs
               (with-executor [exe]
                 (apply cc/mapv
                        (fn [& args]
                          (future-via [exe]
                            (apply f args)))
                        chunks))]
           (recur (cc/for [coll colls]
                    (drop n coll))
                  (into result futs))))))

   #_
   (lazy-seq
    (let [chunks
          (cons (->> coll (take n) seq)
                (cc/for [coll colls]
                  (->> coll (take n) seq)))]
      (when (some some? chunks)
        (let [futs
              (with-executor [exe]
                (apply cc/mapv
                       (fn [& args]
                         (future-via [exe]
                           (apply f args)))
                       chunks))]
          (concat futs
                  (apply nmap
                         n
                         f
                         (drop n coll)
                         (cc/for [coll colls]
                           (drop n coll))))))))))






;; ----------------

(comment

  (defmacro with-executor
  "
  Run a block of code with a new instance of a virtual task executor
  bound to the `bind` symbol. The executor gets closed when exiting
  the macro. Guarantees that all the submitted tasks will be completed
  before closing the executor.
  "
  [[bind] & body]
  `(with-open [~bind (Executors/newVirtualThreadPerTaskExecutor)]
     ~@body))

(defmacro future-via
  "
  Submit a block of code to the given executor service.
  Return a future.
  "
  {:style/indent 1}
  [[^ExecutorService exe] & body]
  `(.submit ~exe
            ^Callable
            (^{:once true} fn* [] ~@body)))

(defn deref-by-one
  "
  A helper function that accepts a lazy sequence of futures
  and derefs items lazily one by one.
  "
  [coll]
  (lazy-seq
   (when-let [f (first coll)]
     (cons (deref f) (deref-by-one (next coll))))))

(defn nmap
  "
  Like map but relies on virtual threads. The `n` parameter
  specifies the window size. Each window is executed under
  an executor and waits until it's closed. Return a lazy
  collection of deref'ed futures.
  "
  ([n f coll]
   (deref-by-one
    (loop [coll coll
           result []]
      (let [chunk (seq (take n coll))]
        (if (nil? chunk)
          result
          (let [futs
                (with-executor [exe]
                  (cc/mapv
                   (fn [item]
                     (future-via [exe]
                       (f item)))
                   chunk))]
            (recur (drop n coll)
                   (into result futs))))))))

  ([n f coll & colls]
   (deref-by-one
    (loop [colls (cons coll colls)
           result []]
      (let [chunks
            (cc/for [coll colls]
              (seq (take n coll)))]
        (if (some nil? chunks)
          result
          (let [futs
                (with-executor [exe]
                  (apply cc/mapv
                         (fn [& args]
                           (future-via [exe]
                             (apply f args)))
                         chunks))]
            (recur (cc/for [coll colls]
                     (drop n coll))
                   (into result futs))))))))))
