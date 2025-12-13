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
  the macro. Guarantees that all the submitted tasks will be completed
  before closing the executor.
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
  TODO
  "
  ^VirtualThread [& body]
  `(-> (Thread/ofVirtual)
       (.name "virtuoso.v3")
       (.start
        (binding-conveyor-fn
         (^{:once true} fn* [] ~@body)))))


(defmacro future
  "
  TODO
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


(defn process-by-one
  "
  A helper function that accepts a lazy sequence of items
  and applies a function to them one by one.
  "
  [f coll]
  (lazy-seq
   (when-let [e (first coll)]
     (cons (f e) (process-by-one f (next coll))))))


(defn deref-by-one
  "
  Deref items lazily one by one.
  "
  [coll]
  (process-by-one deref coll))


(defn fmap
  "
  Like map but relies on virtual threads. The `n` parameter
  specifies the window size. Each window is executed under
  a dedicated virtual executor which gets closed afterwards.
  Return a lazy chunked collection of futures.
  "
  ([n f coll]
   (lazy-seq
    (when-let [chunk (->> coll (take n) seq)]
      (concat (with-executor [exe]
                (cc/vec
                 (cc/for [item chunk]
                   (future-via [exe]
                     (f item)))))
              (fmap n f (drop n coll))))))

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
                (apply fmap
                       n
                       f
                       (drop n coll)
                       (cc/for [coll colls]
                         (drop n coll)))))))))


(defn map222
  "
  Like `map` but run each function in a virtual future.
  Return a lazy sequence that derefs futures when
  iterating.
  "
  ([f coll]
   (deref-by-one
    (with-executor [exe]
      (cc/mapv (fn [item]
                 (future-via [exe]
                   (f item)))
               coll))))

  ([f coll & colls]
   (deref-by-one
    (with-executor [exe]
      (apply cc/mapv
             (fn [& items]
               (future-via [exe]
                 (apply f items)))
             coll
             colls)))))


(defn map
  "
  Like fmap but wrapped with an extra lazy layer
  that derefs futures one by one when iterating
  the result.
  "
  ([n f coll]
   (deref-by-one
    (fmap n f coll)))
  ([n f coll & colls]
   (deref-by-one
    (apply fmap n f coll colls))))


(defmacro pvalues
  "
  Run each form in a dedicated virtual executor and
  close it afterwards. Return a sequence of deref'ed
  futures (by one).
  "
  [& forms]
  (let [exe (gensym "exe")]
    `(deref-by-one
      (with-executor [~exe]
        [~@(cc/for [form forms]
             `(future-via [~exe]
                ~form))]))))


(defmacro for
  "
  TODO
  "
  [bindings & body]
  `(deref-by-one
    (with-executor [exe#]
      (doall
       (cc/for [~@bindings]
         (future-via [exe#]
           ~@body))))))
