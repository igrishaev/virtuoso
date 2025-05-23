# Virtuoso

[virtual-threads]: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html

A small wrapper on top of [virtual threads][virtual-threads] introduced in Java
21.

<!-- toc -->

- [About](#about)
- [Installation](#installation)
- [V2 API (new)](#v2-api-new)
- [V1 API (old)](#v1-api-old)
- [Measurements](#measurements)
- [Links and Resources](#links-and-resources)
- [License](#license)

<!-- tocstop -->

## About

The recent release of Java 21 introduced virtual threads to the scene. It's a
nice feature that allows you to run imperative code, such as it was written in
an asynchronous way. This library is a naive attempt to gain something from the
virtual threads.

## Installation

Lein

~~~clojure
[com.github.igrishaev/virtuoso "0.1.1"]
~~~

Deps/CLI

~~~clojure
{com.github.igrishaev/virtuoso {:mvn/version "0.1.1"}}
~~~

## V2 API (new)

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

## V1 API (old)

First, import the library:

~~~clojure
(require '[virtuoso.core :as v])
~~~

**with-executor**

The `with-executor` wraps a block of code binding a new instance of
`VirtualThreadPerTaskExecutor` to the passed symbol:

~~~clojure
(v/with-executor [exe]
  (do-this ...)
  (do-that ...))
~~~

Above, the executor is bound to the `exe` symbol. Exiting from the macro will
trigger closing the executor, which, in turn, leads to blocking until all the
tasks sent to it are complete. The `with-executor` macro, although it might be
used on your code, is instead a building material for other macros.


**future-via**

The `future-via` macro spawns a new virtual future through a previously open
executor. You can generate as many futures as you want due to the nature of
virtual threads: there might be millions of them.

~~~clojure
(v/with-executor [exe]
  (let [f1 (v/future-via exe
             (do-this ...))
        f2 (v/future-via exe
             (do-that ...))]
    [@f1 @f2]))
~~~

Virtual futures give performance gain only when the code they wrap makes
IO. Instead, if you run CPU-based computations in virtual threads, the
performance suffers due to continuations and moving the stack trace from the
stack to the heap and back.

**futures(!)**

The `futures` macro takes a series of forms. It spawns a new virtual thread
executor and wraps each form into a future bound to that executor. The result is
a vector of `Future` objects. To obtain values, pass the result through
`(map/mapv deref ...)`:

~~~clojure
(let [futs
      (v/futures
       (io-heavy-task-1 ...)
       (io-heavy-task-2 ...)
       (io-heavy-task-3 ...))]
  (mapv deref futs))
~~~

Right before you exit the macro, it closes the executor, which leads to blicking
until all the tasks are complete.

Pay attention that `deref`-ing a failed future leads to throwing an
exception. That's why the macro doesn't dereference the futures for you, as it
doesn't know how to handle errors. But if you don't care about exception
handling, there is a `futures!` macro that does it for you:

~~~clojure
(v/futures!
  (io-heavy-task-1 ...)
  (io-heavy-task-2 ...)
  (io-heavy-task-3 ...))
~~~

The result will be vector of dereferenced values.

**thread**

The `thread` macro spawns and starts a new virtual thread using the
`(Thread/ofVirtual)` call. Threads in Java do not return values; they can only
be `join`-ed or interrupted. Use this macro when interested in a `Thread` object
but not the result.

~~~clojure
(let [thread1
      (v/thread
        (some-long-task ...))

      thread2
      (v/thread
        (some-long-task ...))]

  (.join thread1)
  (.join thread2))
~~~

**pmap(!)**

The `pmap` function acts like the standard `clojure.core/pmap`: it takes a
function and a collection (or more collections). It opens a new virtual executor
and submits each calculation step to the executor. The result is a vector of
futures. The function closes the executor afterwards, blocking until all the
tasks are complete.

~~~clojure
(let [futs
      (v/pmap get-user-from-api [1 2 3])]
  (mapv deref futs))
~~~

Or:

~~~clojure
(let [futs
      (v/pmap get-some-entity                ;; assuming it accepts id and status
              [1 2 3]                        ;; ids
              ["active" "pending" "deleted"] ;; statuses
              )]
  (mapv deref futs))
~~~

The `pmap!` version of this function dereferences all the results for you with
no exception handling:

~~~clojure
(v/pmap! get-user-from-api [1 2 3])
;; [{:id 1...}, {:id 2...}, {:id 3...}]
~~~

**each(!)**

The `each` macro is a wrapper on top of `pmap`. It binds each item from a
collection to a given symbol and submits a code block into a virtual
executor. The result is a vector of futures; exiting the macro closes the
executor.

~~~clojure
(let [futs
      (v/each [id [1 2 3]]
        (log/info...)
        (try
          (get-entity-by-id id)
          (catch Throwable e
            (log/error e ...))))]
  (is (= [{...}, {...}, {...}] (mapv deref futs))))
~~~

The `each!` macro acts the same but dereferences all the futures with no error handling.

## Measurements

There is a development `dev/src/bench.clj` file with some trivial
measurements. Imagine you want to download 100 of URLs. You can do it
sequentially with `mapv`, semi-parallel with `pmap`, and fully parallel with
`pmap` from this library. Here are the timings made on my machine:

~~~clojure
(time
 (count
  (map download URLS)))
"Elapsed time: 45846.601717 msecs"

(time
 (count
  (pmap download URLS)))
"Elapsed time: 3343.254302 msecs"

(time
 (count
  (v/pmap! download URLS)))
"Elapsed time: 1452.514165 msecs"
~~~

45, 3.3, and 1.4 seconds favour the virtual threads approach.

## Links and Resources

The following links helped me a lot to dive into virtual threads, and I highly
recommend reading and watching them:

- [Virtual Threads | Oracle Help Center](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [Java 21 new feature: Virtual Threads #RoadTo21](https://www.youtube.com/watch?v=5E0LU85EnTI)

## License

~~~
©©©©©©©©©©©©©©©©©©©©©©©©©©©©©©©©©©
Ivan Grishaev, 2023. © UNLICENSE ©
©©©©©©©©©©©©©©©©©©©©©©©©©©©©©©©©©©
~~~
