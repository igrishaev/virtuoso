(ns virtuoso.v2
  (:refer-clojure :exclude [future
                            pmap
                            map
                            for
                            pvalues])
  (:import
   clojure.lang.RT
   java.util.Iterator
   java.util.concurrent.Callable
   java.util.concurrent.ExecutorService
   java.util.concurrent.Executors))


(set! *warn-on-reflection* true)


(alias 'cc 'clojure.core)


(def ^ExecutorService -EXECUTOR
  (Executors/newVirtualThreadPerTaskExecutor))


(defn underef [coll]
  (lazy-seq
   (when-let [f (first coll)]
     (cons (deref f) (underef (next coll))))))


(defmacro future
  "
  "
  [& body]
  `(.submit -EXECUTOR
            ^Callable
            (^{:once true} fn* [] ~@body)))


(defmacro pvalues [& forms]
  `(underef
    (list ~@(cc/for [form forms]
              `(future ~form)))))


(defn map
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
              (apply println items)
              (future (apply f items)))
            coll
            colls)))))


(defmacro for [seq-exprs body-expr]
  `(underef
    (doall
     (cc/for ~seq-exprs
       (future ~body-expr)))))


;; thread
