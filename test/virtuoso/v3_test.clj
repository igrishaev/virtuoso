(ns virtuoso.v3-test
  (:import
   (java.lang VirtualThread))
  (:require
   [clojure.test :refer [deftest is]]
   [virtuoso.v3 :as v]))

(set! *warn-on-reflection* true)

(def ^:dynamic *var* nil)

(deftest test-future-1
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

    (is (= 52 @f)))

  (with-redefs [double? (constantly ::abc)]
    (let [f (binding [*var* 42]
              (let [f (future
                        (double? 42))]
                (Thread/sleep 100)
                f))]
      (is (= ::abc @f)))))


(deftest test-pvalues
  (let [capture!
        (atom #{})

        result
        (v/pvalues
          (let [result (+ 1 1)]
            (swap! capture! conj :a)
            result)
          (let [result (/ 0 0)]
            (swap! capture! conj :b)
            result)
          (let [result (+ 2 2)]
            (swap! capture! conj :c)
            result))]

    (is (= #{:a :c}
           @capture!))

    (is (= 2
           (first result)))

    (try
      (nth result 1)
      (is false)
      (catch Exception e
        (is true)))))

(deftest test-thread
  (let [t (v/thread
            (Thread/sleep 1000)
            (+ 1 2))]
    (is (instance? VirtualThread t))
    (is (.isAlive t))
    (.join t)
    (is (not (.isAlive t)))))

(deftest test-future-2
  (let [f (v/future 1)]
    (is (future? f))
    (is (= 1 @f)))

  (let [f (v/future
            (/ 0 0))]
    (is (future? f))
    (try
      @f
      (is false)
      (catch Exception e
        (is e)))))

(deftest test-map
  (let [result (v/map inc [1 2 3])]
    (is (= [2 3 4] result)))

  (let [result (v/map / [4 6 3] [2 2 0])]
    (is (= 2 (nth result 0)))
    (is (= 3 (nth result 1)))
    (try
      (nth result 2)
      (is false)
      (catch Exception e
        (is e))))

  (let [result (v/map / [4 6 3] [2 2 3 0])]
    (is (= [2 3 1] result))))

(deftest test-pmap
  (let [capture!
        (atom #{})

        result
        (v/pmap 3
                (fn [a b]
                  (swap! capture! conj [a b])
                  (/ a b))
                [3 2 1 0]
                [3 2 1 0])]

    (is (= #{} @capture!))
    (is (= 1 (first result)))
    (is (= #{[2 2] [3 3] [1 1]} @capture!))))

(deftest test-)



;; with-executor
;; future-via


(defn sequential-pagination
  [job-config
   {:keys [request response next-request req-fn request-sleep-ms] :as params}
   flow-data]
  (iteration (fn [[prev-request prev-response]]
               (when request-sleep-ms
                 (System/gc)
                 (Thread/sleep (min request-sleep-ms 15000)))
               (when-some [req (conditionals/next-request-on-condition
                                next-request
                                job-config
                                params
                                (assoc flow-data
                                       :prev-request  prev-request
                                       :prev-response prev-response))]
                 [req (req-fn req)]))
             :initk [request response]))

(->> data
     (partition-all batch-size)
     (process-batches! s3-client))

(loop [data data]
  (when-let [chunk (seq (take batch-size data))]
    (process-batches! chunk)
    (recur (drop batch-size data))))



(defn get-request [job-config
                   {:as params :keys [next-request
                                      prev-request
                                      prev-response]}
                   flow-data]
  (conditionals/next-request-on-condition
   next-request
   job-config
   params
   (assoc flow-data
          :prev-request  prev-request
          :prev-response prev-response)))


(defn sequential-pagination
  [job-config
   {:as params :keys [req-fn]}
   flow-data]
  (loop [params params]
    (when-let [request (get-request job-config params flow-data)]
      (let [response (req-fn request)]
        (recur (assoc params
                      :prev-request request
                      :prev-response response))))))
