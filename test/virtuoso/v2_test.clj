(ns virtuoso.v2-test
  (:require
   [clojure.test :refer [deftest is]]
   [virtuoso.v2 :as v]))

(deftest test-future-ok
  (let [fut
        (v/future (println 42)
                  (do 1 2 3))]
    (is (future? fut))
    (is (= 3 @fut)))
  (let [fut
        (v/future (/ 0 0))]
    (is (future? fut))
    (try
      @fut
      (is false)
      (catch java.util.concurrent.ExecutionException e
        (is (= "Divide by zero"
               (-> e ex-cause ex-message)))))))


(deftest test-pvalues-ok
  (let [items
        (v/pvalues 1
                   2
                   3)]
    (is (= [1 2 3] items)))
  (let [items
        (v/pvalues 1
                   (/ 0 0)
                   3)]
    (is (seq? items))
    (is (= 1
           (first items)))
    (try
      (second items)
      (is false)
      (catch java.util.concurrent.ExecutionException e
        (is true)))
    (try
      (last items)
      (is false)
      (catch java.util.concurrent.ExecutionException e
        (is true)))))

(deftest test-map-ok
  (let [items
        (v/map / [8 10 3] [4 5 0])]
    (is (= 2 (first items)))
    (is (= 2 (second items)))
    (try
      (last items)
      (is false)
      (catch java.util.concurrent.ExecutionException e
        (is true))))
  (let [items
        (v/map (fn [x]
                 (/ x x)) [1 2 3 0])]
    (is (= 1 (first items)))
    (is (= 1 (second items)))
    (try
      (last items)
      (is false)
      (catch java.util.concurrent.ExecutionException e
        (is true)))))

(deftest test-for-ok
  (let [items
        (v/for [x [1 2 3 0]]
          (/ x x))]
    (is (= 1 (first items)))
    (try
      (last items)
      (is false)
      (catch java.util.concurrent.ExecutionException e
        (is true))))

  (let [t1
        (System/currentTimeMillis)

        items
        (v/for [x (range 99999)]
          (do (Thread/sleep 1000)
              x))

        _
        (doall items)

        t2
        (System/currentTimeMillis)]

    (is (< (- t2 t1) 2000))))


(deftest test-thread-ok
  (let [time1
        (System/currentTimeMillis)

        t1 (v/thread
             (do (Thread/sleep 1000)
                 1))
        t2 (v/thread
             (do (Thread/sleep 2000)
                 2))

        _ (.join t1)
        _ (.join t2)

        time2
        (System/currentTimeMillis)]

    (is (< (- time2 time1) 2200))))
