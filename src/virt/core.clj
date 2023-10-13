(ns virt.core
  (:refer-clojure :exclude [future pmap])
  (:import
   clojure.lang.RT
   java.util.Iterator
   java.util.concurrent.Callable
   java.util.concurrent.Executors
   java.util.concurrent.ExecutorService))


(defmacro deref-all [futs]
  `(mapv deref ~futs))


(defmacro with-executor [[bind] & body]
  `(with-open [~bind (Executors/newVirtualThreadPerTaskExecutor)]
     ~@body))


(defmacro future-via
  {:style/indent 1}
  [[executor] & body]
  `(.submit ~executor
            (reify Callable
              (call [this#]
                ~@body))))


(defmacro future [& body]
  `(with-executor [executor#]
     (future-via [executor#]
       ~@body)))


(defmacro futures
  [& forms]
  (let [exe-sym (gensym "executor")]
    `(with-executor [~exe-sym]
       [~@(for [form forms]
            `(future-via [~exe-sym]
               ~form))])))


(defmacro futures! [& forms]
  `(deref-all (futures ~@forms)))


(defmacro thread [& body]
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
                f (future-via [executor]
                    (apply func xs))]
            (recur (conj! acc! f)))
          (persistent! acc!))))))


(defn pmap

  ([func coll]
   (with-executor [executor]
     (let [iter (->iter coll)]
       (loop [acc! (transient [])]
         (if (has-next? iter)
           (let [x (get-next iter)
                 f (future-via [executor]
                     (func x))]
             (recur (conj! acc! f)))
           (persistent! acc!))))))

  ([func coll & colls]
   (pmap-multi func (cons coll colls))))


(defn pmap!

  ([func coll]
   (deref-all (pmap func coll)))

  ([func coll & colls]
   (deref-all (pmap-multi func (cons coll colls)))))


(defmacro each
  {:style/indent 1}
  [[item coll] & body]
  `(pmap (fn [~item] ~@body) ~coll))


(defmacro each!
  {:style/indent 1}
  [[item coll] & body]
  `(deref-all (each [~item ~coll] ~@body)))
