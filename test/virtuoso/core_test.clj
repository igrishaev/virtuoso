(ns virtuoso.core-test
  (:import
   java.util.concurrent.RejectedExecutionException
   java.util.concurrent.Callable
   java.util.concurrent.ExecutorService)
  (:require
   [clojure.test :refer [deftest is]]
   [virtuoso.core :as v]))


(deftest test-executor

  (let [capture! (atom nil)]

    (v/with-executor [exe]
      (reset! capture! exe))

    (let [^ExecutorService exe @capture!]
      (is (instance? ExecutorService exe))

      (try
        (.submit exe (reify Callable
                       (call [_]
                         (+ 1 2))))
        (is false)
        (catch RejectedExecutionException e
          (is true))))))


(deftest test-thread
  (let [capture! (atom {:foo 1})
        t (v/thread
            (swap! capture! update :foo inc))]
    (.join t)
    (is (= {:foo 2} @capture!))))


(deftest test-futures

  (let [fut (v/future (+ 1 2 3))]
    (is (future? fut))
    (is (= 6 @fut)))

  (v/with-executor [exe]
    (let [a 3
          b 4

          f1 (v/future-via exe
               (+ a b))
          f2 (v/future-via exe
               (* a b))

          res
          [@f1 @f2]]

      (is (= [7 12] res))))

  (let [futs
        (v/futures
         (+ 1 2 3)
         (* 1 2 3)
         (assoc {:foo 42} :bar 1))]

    (is (= 3 (count futs)))
    (is (= [6 6 {:foo 42, :bar 1}] (mapv deref futs))))

  (let [values
        (v/futures!
         (+ 1 2 3)
         (* 1 2 3)
         (assoc {:foo 42} :bar 1))]

    (is (= [6 6 {:foo 42, :bar 1}] values))))


(deftest test-pmap

  (let [futs
        (v/pmap inc [1 2 3])]
    (is (= [2 3 4] (mapv deref futs))))

  (let [futs
        (v/pmap + [1 2 3] [2 3 4])]
    (is (= [3 5 7] (mapv deref futs))))

  (let [values
        (v/pmap! inc [1 2 3])]
    (is (= [2 3 4] values)))

  (let [values
        (v/pmap! + [1 2 3] [2 3 4])]
    (is (= [3 5 7] values))))


(deftest test-each

  (let [futs
        (v/each [x [1 2 3]]
          (inc x))]
    (is (= [2 3 4] (mapv deref futs))))

  (let [values
        (v/each! [x [1 2 3]]
          (inc x))]
    (is (= [2 3 4] values))))
