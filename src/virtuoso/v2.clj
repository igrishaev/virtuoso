(ns virtuoso.v2
  "
  A set of Clojure-like functions and macros
  that act using a global virtual executor.
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


(def ^ExecutorService -EXECUTOR
  (Executors/newVirtualThreadPerTaskExecutor))


(defn underef
  "
  A helper function that accepts a lazy sequence
  of futures and derefs items lazily one by one.
  "
  [coll]
  (lazy-seq
   (when-let [f (first coll)]
     (cons (deref f) (underef (next coll))))))


(defmacro future
  "
  Wraps an arbitrary block of code into a future
  bound to global virtual executor service.
  "
  ^Future [& body]
  `(.submit -EXECUTOR
            ^Callable
            (^{:once true} fn* [] ~@body)))


(defmacro pvalues
  "
  Wrap each form into a virtual future and return
  a lazy sequence that, while iterating, derefs
  them.
  "
  [& forms]
  `(underef
    (list ~@(cc/for [form forms]
              `(future ~form)))))


(defn map
  "
  Like `map` but run each function in a virtual future.
  Return a lazy sequence that derefs futures when
  iterating.
  "
  ([f coll]
   (underef
    (doall
     (cc/map (fn [item]
               (future (f item)))
             coll))))

  ([f coll & colls]
   (underef
    (doall
     (apply cc/map
            (fn [& items]
              (future (apply f items)))
            coll
            colls)))))


(defmacro for
  "
  Like `for` but wraps each body expression into a virtual
  future. Return a lazy sequence that derefs them when
  iterating.
  "
  [seq-exprs body-expr]
  `(underef
    (doall
     (cc/for ~seq-exprs
       (future ~body-expr)))))


(defmacro thread
  "
  Spawn and run a new virtual thread.
  "
  ^VirtualThread [& body]
  `(-> (Thread/ofVirtual)
       (.name "virtuoso.v2")
       (.start
        (reify Runnable
          (run [this#]
            ~@body)))))


(defonce ^Thread -shutdown-hook
  (new Thread (fn []
                (.close -EXECUTOR))))


(defonce ___
  (-> (Runtime/getRuntime)
      (.addShutdownHook -shutdown-hook)))
