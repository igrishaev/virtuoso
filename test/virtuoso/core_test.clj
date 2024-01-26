(ns virtuoso.core-test
  (:import
   java.util.concurrent.RejectedExecutionException
   java.util.concurrent.Callable
   java.util.concurrent.ExecutorService)
  (:require
   [clojure.test :refer [deftest is]]
   [virtuoso.core :as v]))


(def ^:dynamic *foo* 0)


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


(deftest test-future

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

      (is (= [7 12] res)))))


(deftest test-pvalues

  (let [values
        (v/pvalues
         (+ 1 2 3)
         (* 1 2 3)
         (assoc {:foo 42} :bar 1))]

    (is (= 3 (count values)))
    (is (= [6 6 {:foo 42, :bar 1}] values))))


(deftest test-map
  (let [result
        (v/map vector [1 2 3] [:a :b])]
    (is (= [[1 :a] [2 :b]]
           result))))


(deftest test-pmap
  (let [result
        (v/pmap 3 + [1 2 3] [4 5 6 7])]
    (is (= [5 7 9] result))))


(deftest test-pmap-widnow
  (let [result
        (v/pmap 2 / [4 3 2 1 0] [4 3 2 1 0])]

    (is (= 1 (nth result 0)))
    (is (= 1 (nth result 2)))

    (try
      (nth result 4)
      (is false)
      (catch Throwable e
        (is true)
        (is (= "java.lang.ArithmeticException: Divide by zero"
               (ex-message e)))))))


(deftest test-frame

  (let [result
        (binding [*foo* 42]
          (v/with-executor [exe]
            (v/future-via exe
              (inc *foo*))))]
    (is (= 43 @result)))

  (let [result
        (binding [*foo* 42]
          (v/map (fn [x]
                   (+ *foo* x))
              [1 2 3]))]
    (is (= [43 44 45] result)))

  (let [result
        (binding [*foo* 42]
          (v/pmap 2 (fn [x]
                      (+ *foo* x))
              [1 2 3 4 5]))]
    (is (= [1 2 3 4 5] result)))

  (let [result
        (binding [*foo* 42]
          (doall
           (v/pmap 2 (fn [x]
                       (+ *foo* x))
               [1 2 3 4 5])))]
    (is (= [43 44 45 46 47] result))))


(deftest test-for
  (let [values
        (v/for [x [1 2 3]]
          (inc x))]
    (is (= [2 3 4] values))))
