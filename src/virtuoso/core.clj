(ns virtuoso.core
  (:refer-clojure :exclude [future pmap map for pvalues])
  (:import
   java.util.concurrent.Callable
   java.util.concurrent.Executors
   java.util.concurrent.ExecutorService
   ))


(alias 'cc 'clojure.core)

(set! *warn-on-reflection* true)


(def ^ExecutorService EXECUTOR
  (Executors/newVirtualThreadPerTaskExecutor))


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
  TODO
  "
  {:style/indent 1}
  [executor & body]
  `(.submit ~executor ^Callable (^{:once true} fn* [] ~@body)))


(defmacro future
  "
  TODO
  "
  [& body]
  `(future-via EXECUTOR ~@body))


(defmacro underef [coll]
  ;; TODO: better
  `(cc/map deref ~coll))


(defmacro pvalues
  "
  Spawn a new virtual executor and perform each form separately
  in a future. Return a sequence of deferenced futures.
  "
  [& forms]
  `(pvalues-via EXECUTOR ~@forms))


(defmacro pvalues-via
  "
  Spawn a new virtual executor and perform each form separately
  in a future. Return a sequence of deferenced futures.
  "
  [executor & forms]
  (let [EXE (gensym "EXE")]
    `(underef
      [~@(cc/for [form forms]
           `(future-via ~executor
              ~form))])))


(defmacro for
  "
  Like `for` but spawn a new virtual executor and wrap
  the body expression with a future. Return a sequence
  of dereferenced futures. `:len`, `:when`, expressions
  are supported.
  "
  [seq-exprs body-expr]
  `(underef
    (with-executor [exe#]
      (doall
       (cc/for ~seq-exprs
         (future-via exe#
           ~body-expr))))))


(defmacro map
  "
  Like `map` but wrap each `f` call with a future
  bound to the temporary virtual executor. Return
  a list of dereferenced futures.
  "
  [f coll & colls]
  (let [ARGS
        (cc/for [_ (cons coll colls)]
          (gensym "X"))]
    `(underef
      (with-executor [exe#]
        (doall
         (cc/map (fn [~@ARGS]
                   (future-via exe#
                     (~f ~@ARGS))) ~coll ~@colls))))))


(defmacro pmap
  "
  Like `pmap` but evaluate each chunk in a dedicated
  virtual executor. The `n` parameter specifies the
  size of chunk. Return a semi-lazy sequence of
  dereferenced futures.
  "
  [n f coll & colls]

  (let [all-colls
        (cons coll colls)

        ARGS
        (cc/for [_ all-colls]
          (gensym "X"))

        COLLS
        (cc/for [_ all-colls]
          (gensym "COLL"))

        N
        (gensym "N")]

    `(let [f# ~f
           ~N ~n
           ~@(interleave COLLS all-colls)

           chunk#
           (fn self# [~@ARGS]
             (lazy-seq
              (let [result#
                    (map f# ~@(for [ARG ARGS]
                                `(take ~N ~ARG)))]
                (when (seq result#)
                  (concat result# (self# ~@(for [ARG ARGS]
                                             `(drop ~N ~ARG))))))))]

       (chunk# ~@COLLS))))
