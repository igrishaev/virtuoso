(ns virt.core
  (:refer-clojure :exclude [future pmap])
  (:import
   clojure.lang.RT
   java.util.Iterator
   java.util.concurrent.Callable
   java.util.concurrent.ExecutorService
   java.util.concurrent.Executors))


(defn deref-all
  "
  Dereference all the futures. Return a vector of values.
  "
  [futs]
  (mapv deref futs))


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
  Spawn a new future using a previously open executor.
  Do not wait for the task to be completed.
  "
  {:style/indent 1}
  [executor & body]
  `(.submit ~executor
            (reify Callable
              (call [this#]
                ~@body))))


(defmacro future
  "
  Spawn a new future using a temporal virtual executor.
  Close the pool afterwards. Block until the task is completed.
  "
  [& body]
  `(with-executor [executor#]
     (future-via executor#
       ~@body)))


(defmacro futures
  "
  Wrap each form into a future bound to a temporal virtual
  executor. Return a vector of futures. Close the pool afterwards
  blocking until all the tasks are complete.
  "
  [& forms]
  (let [exe-sym (gensym "executor")]
    `(with-executor [~exe-sym]
       [~@(for [form forms]
            `(future-via ~exe-sym
               ~form))])))


(defmacro futures!
  "
  Like `futures` but dereference all the futures. Return
  a vector of dereferenced values. Should any task fail,
  trigger an exception."
  [& forms]
  `(deref-all (futures ~@forms)))


(defmacro thread
  "
  Spawn and run a new virtual thread.
  "
  [& body]
  `(.start (Thread/ofVirtual)
           (reify Runnable
             (run [this#]
               ~@body))))


(defn ->iter ^Iterator [coll]
  (RT/iter coll))


(defn has-next? [^Iterator iter]
  (.hasNext iter))


(defn get-next [^Iterator iter]
  (.next iter))


(defn pmap-multi [func colls]
  (let [iters (mapv ->iter colls)]
    (with-executor [executor]
      (loop [acc! (transient [])]
        (if (every? has-next? iters)
          (let [xs (mapv get-next iters)
                f (future-via executor
                    (apply func xs))]
            (recur (conj! acc! f)))
          (persistent! acc!))))))


(defn pmap
  "
  Like `clojure.core/pmap` but wrap each step into a future
  bound to a temporal virtual executor. Return a vector of
  futures. Close the pool afterwards which leads to blocking
  until all the tasks are completed.
  "

  ([func coll]
   (with-executor [executor]
     (let [iter (->iter coll)]
       (loop [acc! (transient [])]
         (if (has-next? iter)
           (let [x (get-next iter)
                 f (future-via executor
                     (func x))]
             (recur (conj! acc! f)))
           (persistent! acc!))))))

  ([func coll & colls]
   (pmap-multi func (cons coll colls))))


(defn pmap!

  "
  Like `pmap` but dereference all the futures. Return a vector
  of values. Should any task fail, throw an exception.
  "

  ([func coll]
   (deref-all (pmap func coll)))

  ([func coll & colls]
   (deref-all (pmap-multi func (cons coll colls)))))


(defmacro each
  "
  Run a block of code for each collection's item. The item
  is bound to the `item` symbol. Return a vector of futures
  each bound to a temporal virtual executor.
  "
  {:style/indent 1}
  [[item coll] & body]
  `(pmap (fn [~item] ~@body) ~coll))


(defmacro each!
  "
  Like `each` but dereference all the futures. Return a vector
  of values. Should any task fail, throw an exception.
  "
  {:style/indent 1}
  [[item coll] & body]
  `(deref-all (each [~item ~coll] ~@body)))
