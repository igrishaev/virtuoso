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

## V3 API (current)

The `virtuoso.v3` namespace provides a number of functions and macros. Most of
them mimic their counterparts from the `clojure.core` namespace, but are
enforced by virtual threads.

~~~clojure
(ns demo
  (:require
   [virtuoso.v3 :as v]))
~~~

**The `thread` macro** runs a block if code in a virtual thread. You'll get an
instance of `java.lang.VirtualThread`. The thread is started immediately. You
can `.join` it if you want.

~~~clojure
(def -t (v/thread
          (Thread/sleep 1000)
          (+ 1 2)))

(.join -t)
nil
~~~

Usually, you cannot return a value from a thread (in a normal way). Use this
macro only when you're not interested in a result.

**The `future` macro** runs a virtual thread similar to the `thread` macro. The
diffenrece is, you'll get an instance of `CompletableFuture` which gets
completed either normally or exceptionally should an exception pops up. The
result can be processed with `deref` or any `future-...` function:

~~~clojure
(def -f (v/future
          (Thread/sleep 1000)
          (+ 1 2)))

(future? -f) ;; true
(future-done? -f) ;; true
@-f ;; 3

(def -failed
  (v/future (/ 0 0)))

(deref -failed)
;; Execution error (ArithmeticException) at ...
;; Divide by zero
~~~

There are **two macros called `with-executor` and `future-via`** working in
pair. The first macro temporary opens a new virtual `ExecutorService` and binds
it to a certain variable. Pass this executor into the `future-via` macro so the
task is bound to this specific executor. The `with-executor` macro closes the
executor when exiting which guarantees all pending tasks are completed (normally
or exceptionally).

~~~clojure
(v/with-executor [exe]
  (let [a (v/future-via [exe]
            (Thread/sleep 1000)
            (+ 1 2))
        b (v/future-via [exe]
            (Thread/sleep 1000)
            (+ 4 5))]
    (+ @a @b)))

;; 12
~~~

**The `map` function** acts like `clojure.core/map` does but:

- for every item, the target function is called in a virtual thread;
- all items are processed immediately without chunking;
- therefore, the amount of virtual threads is unlimited;
- the result is a lazy seq of `deref`-ed items processed one by one.

~~~clojure
(def -result
  (v/map (fn [a b]
           (Thread/sleep 100)
           (+ a b))
         (range 10)
         (range 10)))

(0 2 4 6 8 10 12 14 16 18)
~~~

Pay attention: if you perform HTTP calls or file IO for each function,
apparently you might hit the standard 1024 `ulimit`. Java will throw an
exception saying "too many open connections" or something. For such cases, it's
better to use the `pmap` function that acts through chunks (see below).

**The `pmap` function** is similar to `clojure.core/pmap` in that direction that
it splits incoming data on chunks. Each chunk of items is served within a
dedicated virtual executor. The next chunk won't start until the current one is
complete. The leading `n` parameter determines the chunk size. While working
with HTTP calls, that's ok to pass 512 or something similar (unless you have a
custom `ulimit` value set).



## V2 API (deprecated)

Moved to a [legacy V2 doc file](doc/v2_api.md).

## V1 API (deprecated)

Moved to a [legacy V1 doc file](doc/v1_api.md).

## Measurements

TODO updated

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
