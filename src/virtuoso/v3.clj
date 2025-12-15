(ns virtuoso.v3
  "
  A set of functions and macros named after
  their clojure.core counterparts but acting
  using virtual threads.
  "
  (:refer-clojure :exclude [future
                            pmap
                            map
                            for
                            pvalues])
  (:import
   (java.lang VirtualThread)
   (java.util.concurrent Callable
                         CompletableFuture
                         Future
                         ExecutorService
                         Executors)))


(set! *warn-on-reflection* true)


(alias 'cc 'clojure.core)


(defmacro with-executor
  "
  Run a block of code with a new instance of a virtual task executor
  bound to the `bind` symbol. The executor gets closed when exiting
  the macro. Guarantees that all the submitted tasks are completed
  before the executor is closed.
  "
  [[bind] & body]
  `(with-open [~bind (Executors/newVirtualThreadPerTaskExecutor)]
     ~@body))


(def binding-conveyor-fn
  @(var cc/binding-conveyor-fn))


(defmacro future-via
  "
  Submit a block of code to the given executor service.
  Return a future.
  "
  {:style/indent 1}
  ^Future [[^ExecutorService exe] & body]
  `(.submit ~exe
            ^Callable
            (binding-conveyor-fn
             (^{:once true} fn* [] ~@body))))


(defmacro thread
  "
  Run a block of code in a virtual thread. Return
  a running VirtualThread instance.
  "
  ^VirtualThread [& body]
  `(-> (Thread/ofVirtual)
       (.name "virtuoso.v3")
       (.start
        (binding-conveyor-fn
         (^{:once true} fn* [] ~@body)))))


(defmacro future
  "
  Run a block of code in a virtual thread. Return
  a CompletableFuture that gets completed either
  successfully or exceptionally depending on how
  the code behaves.
  "
  ^CompletableFuture [& body]
  `(let [future# (new CompletableFuture)]
     (thread
       (let [[result# e#]
             (try
               [(do ~@body) nil]
               (catch Throwable e#
                 [nil e#]))]
         (if e#
           (.completeExceptionally future# e#)
           (.complete future# result#))))
     future#))


(defn deref-all
  "
  Deref all items from a collection lazily one by one.
  "
  [coll]
  (lazy-seq
   (when-let [e (first coll)]
     (cons (deref e) (deref-all (next coll))))))


(defn map
  "
  Like `map` but each function is running in a  virtual executor
  producing a future. Return a vector of completed/failed futures
  (no pending ones).
  "
  ([f coll]
   (with-executor [exe]
     (cc/mapv (fn [item]
                (future-via [exe]
                  (f item)))
              coll)))

  ([f coll & colls]
   (with-executor [exe]
     (apply cc/mapv
            (fn [& items]
              (future-via [exe]
                (apply f items)))
            coll
            colls))))

(defn pmap
  "
  Like `pmap` where each chunk of items is executed in
  a dedicated virtual executor. The `n` parameter specifies
  the chunk size. Each chunk gets completely finished and
  the executor is closed before proceeding to the next chunk.
  Return a lazy sequence of completed/failed futures.
  "
  ([n f coll]
   (lazy-seq
    (when-let [chunk (->> coll (take n) seq)]
      (concat (with-executor [exe]
                (cc/vec
                 (cc/for [item chunk]
                   (future-via [exe]
                     (f item)))))
              (pmap n f (drop n coll))))))

  ([n f coll & colls]
   (lazy-seq
    (let [chunks
          (cons (->> coll (take n) seq)
                (cc/for [coll colls]
                  (->> coll (take n) seq)))]
      (when (every? some? chunks)
        (concat (with-executor [exe]
                  (apply cc/mapv
                         (fn [& args]
                           (future-via [exe]
                             (apply f args)))
                         chunks))
                (apply pmap
                       n
                       f
                       (drop n coll)
                       (cc/for [coll colls]
                         (drop n coll)))))))))


(defmacro pvalues
  "
  Run forms in a dedicated virtual executor and close it
  afterwards. Return a vector of completed/failed futures.
  "
  [& forms]
  (let [exe (gensym "exe")]
    `(with-executor [~exe]
       [~@(cc/for [form forms]
            `(future-via [~exe]
               ~form))])))


(defmacro for
  "
  Like `for` but performs all body expressions in a virtual
  executor. The executor gets closed afterwards. Return
  a realized sequence of completed/failed futures.
  "
  [bindings & body]
  `(with-executor [exe#]
     (doall
      (cc/for [~@bindings]
        (future-via [exe#]
          ~@body)))))
