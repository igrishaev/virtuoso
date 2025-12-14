The new namespace called `virtuoso.v2` brings some functions and macros that
already present in the `virtuoso.core` namespace. To prevent things from
breaking, I decided to put them into a new namespace. Personally I find `v2`
more convenient for usage but this is up to you.

The `v2` API provides utilities named after their Clojure counterparts,
e.g. `map`, `pvalues` and so on. But under the hood, they use a global virtual
executor. This executor gets closed on JVM shutdown.

Import the namespace:

~~~clojure
(ns some.project
  (:require
   [virtuoso.v2 :as v]))
~~~

**The `future` macro** acts like a regular future but is served using a global
virtual executor service:

~~~clojure
(def -fut
  (v/future
    (let [a 1 b 2] (+ a b))))

-fut
#object[java.util... 0x1f3aa45f "java.util.concurrent...@1f3aa45f[Completed normally]"]

@-fut
3
~~~

The macro accepts an arbitrary block of code that gets executed into a future.

**The `pvalues` macro** accepts a number of forms and runs each into a
future. The result is a lazy sequence of dereferenced values:

~~~clojure
(def -items
    (v/pvalues (+ 3 4)
               (Thread/sleep 1000)
               (let [a 3]
                 (* a a))))

-items
(7 nil 9)
~~~

Pay attention all the futures get run immediately but the process of
dereferencing is lazy. They get `deref`-ed one by one as you iterate the
result. Thus, you can easily spot an exception should it pop up:

~~~clojure
(def -items
    (v/pvalues (/ 3 2)
               (/ 3 1)
               (/ 3 0)))

(first -items)
3/2

(second -items)
3

(last -items) ;; only now throws
;; Execution error (ArithmeticException) at virtuoso.v2/fn...
;; Divide by zero
~~~

**The `map` function** is similar to the standard `map` but performs each
function call in a virtual future. All the steps are fired without chunking. The
result is a lazy sequence of dereferenced values.

~~~clojure
(def -items
    (v/map (fn [x] (/ 10 x)) [5 4 3 2 1 0]))

;; don't touch the last item
(take 5 -items)
;; (2 5/2 10/3 5 10)

;; touch it
(last -items)
;; Execution error (ArithmeticException) at virtuoso.v2/fn...
;; Divide by zero
~~~

**The `for`** macro acts like `for` but wraps each body expression into a
future. All the futures are fired at once with no chunking. The result is a lazy
sequence of dereferenced values. You can use `:let`, `:when`, and other nested
forms:

~~~clojure
(def -items
    (v/for [a [:a :b :c]
        b [1 2 3 4 5]
        :when (and (not= a :b) (not= b 3))]
    {:a a :b b}))

-items
({:a :a, :b 1}
 {:a :a, :b 2}
 {:a :a, :b 4}
 {:a :a, :b 5}
 {:a :c, :b 1}
 {:a :c, :b 2}
 {:a :c, :b 4}
 {:a :c, :b 5})
~~~

The `thread` macro just creates and starts a new virtual thread out from a block
of code. Useful if you'd like to deal with `Thead` instances:

~~~clojure
(let [t1 (v/thread (Thread/sleep 1000) (+ 1 2))
      t2 (v/thread (Thread/sleep 2000) (* 3 2))]
    (.join t1)
    (.join t2)
    (println "both are done"))
~~~

The `v2` namespace, when loaded, adds its own JVM shutdown hook as follows:

~~~clojure
(defonce ^Thread -shutdown-hook
  (new Thread (fn []
                (.close -EXECUTOR))))

(defonce ___
  (-> (Runtime/getRuntime)
      (.addShutdownHook -shutdown-hook)))
~~~

The global executor will be closed on JVM shutdown.
