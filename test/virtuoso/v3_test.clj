(ns virtuoso.v3-test
  (:require
   [clojure.test :refer [deftest is]]
   [virtuoso.v3 :as v]))

(set! *warn-on-reflection* true)

(def ^:dynamic *var* nil)

(deftest test-future
  (let [f (v/future (+ 1 2))]
    (future? f)
    (is (= 3 @f)))

  (let [f (v/future (/ 1 0))]
    (future? f)
    (try
      @f
      (is false)
      (catch Exception e
        (is (instance? java.util.concurrent.ExecutionException
                       e))
        (is (= "java.lang.ArithmeticException: Divide by zero"
               (ex-message e)))
        (is (instance? ArithmeticException
                       (ex-cause e))))))

  (let [f
        (v/future
          (Thread/sleep 3000)
          ::done)
        v
        (deref f 1000 ::timeout)]

    (is (= v ::timeout)))

  (let [f
        (binding [*var* 42]
          (let [f (v/future
                    (+ *var* 10))]
            (Thread/sleep 100)
            f))]

    (is (= 52 @f))



    )





  )

;; with-executor
;; future-via
;; overrides
;; bindings?
;; thread
;; future
;; map
;; fmap
;; pmap capture evaluation
;; pvalues
