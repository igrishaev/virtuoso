# Virtuoso

[virtual-threads]: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html

A small wrapper on top of [virtual threads][virtual-threads] introduced in Java
21.

<!-- toc -->

- [About](#about)
- [Installation](#installation)
- [V3 API (current)](#v3-api-current)
- [V2 API (deprecated)](#v2-api-deprecated)
- [V1 API (deprecated)](#v1-api-deprecated)
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

*TL;DR: why making the new API? Because unlike v1 and v2, the third version was
heavily tested in production. It has spotted some weaknesses in v2 but I don't
want to introduce breaking changes. Thus, it's safer to ship a new module (done
right this time, I hope).*

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

Pay attention: if you perform HTTP calls or file IO for each item, apparently
you might hit the global 1024 `ulimit` constraint. Java will throw an exception
saying "too many open connections" or something. For such cases, it's better to
use the `pmap` function that acts through chunks (see below).

**The `pmap` function** is similar to `clojure.core/pmap` as it splits incoming
data on chunks. Each chunk of items is served within a dedicated virtual
executor. The next chunk won't start until the current one is complete. The
leading `n` parameter determines the chunk size. While working with HTTP calls,
that's ok to pass 512 or something similar (unless you have a custom `ulimit`
alue set). The function returns a lazy sequence of `deref`-ed values.

~~~clojure
(def -result
  (v/pmap 512
          (fn [a b]
            (Thread/sleep 1000)
            (+ a b))
          (range 1000)
          (range 1000)))

(count -result) ;; takes ~2 seconds
1000
~~~

Better example: download 50k files from S3 by chunks of 1000:

~~~clojure
(def -result
  (v/pmap 1000
          (fn [url]
            (-> url
                (client/get {:as :stream})
                (:body)
                (process-input-stream)))
          (get-urls-to-fetch...)))
~~~

**The `fmap` function** is a low-level function which `pmap` is based on. It
returns a chunked sequence of futures. It's up to you how to handle them:

~~~clojure
(def -futs
  (v/fmap 512
          (fn [a b]
            (Thread/sleep 100)
            (+ a b))
          (range 1000)
          (range 1000)))

(take 5 -futs)

(#object[ThreadBoundFuture ...[Completed normally]"]
 #object[ThreadBoundFuture ...[Completed normally]"]
 #object[ThreadBoundFuture ...[Completed normally]"]
 #object[ThreadBoundFuture ...[Completed normally]"]
 #object[ThreadBoundFuture ...[Completed normally]"])
~~~

**The `pvalues` macro** acts like `clojure.core/pvalues` forms are executed
within a virtual executor which gets closed afterwards. The result a lazy
sequence which iterating, `deref`s futures.

~~~clojure
(v/pvalues
  (+ 1 2)
  (let [a 3 b 4]
    (Thread/sleep 100)
    (+ a b))
  (* 5 6))

;; (3 7 30)
~~~

**The `for` macro** mimics the standard `clojure.core/for` but each body is run
in a virtual future. These futures are global meaning they are not bound to a
dedicated virtual executor. The result is a sequence of `deref`-ed values:

~~~clojure
(v/for [a [1 2 3]
        b [:a :b :c]
        :when (not= [a b] [2 :b])
        :let [c (* a a)]]
  {:c c :b b})

({:c 1, :b :a}
 {:c 1, :b :b}
 {:c 1, :b :c}
 {:c 4, :b :a}
 {:c 4, :b :c}
 {:c 9, :b :a}
 {:c 9, :b :b}
 {:c 9, :b :c})
~~~

## V2 API (deprecated)

Moved to a [legacy V2 doc file](doc/v2_api.md).

## V1 API (deprecated)

Moved to a [legacy V1 doc file](doc/v1_api.md).

## Measurements

There is a development `dev/src/bench.clj` module with some benchmarks. Imagine
we want to download 100 large files using `map`, `pmap` and virtual
threads. Before we do this, let's mimic real environment as follows:

- install and run nginx;
- put a large binary file into the static folder;
- for that file, limit the throughput:

~~~text
server {
    listen       8080;
    server_name  localhost;
    ...
    location /hugefile.bin {
        root html;
        limit_rate 500k;
    }
}
~~~

Now when you `curl` that file, it will be v-v-very slow.

The idea behind this trick is to mimic **real** IO expectation. Without limiting
throughput, the standard `map` outperforms both `pmap` and virtual threads just
because networking is too fast.

Now that the file is served in a slow manner, prepare a function that downloads
it into nowhere:

~~~clojure
(def URL "http://127.0.0.1:8080/hugefile.bin")

(defn download [i]
  (with-open [in ^java.io.InputStream
              (:body (client/get URL {:as :stream}))
              out
              (java.io.OutputStream/nullOutputStream)]
    (.transferTo in out)))
~~~

Let's download it 100 times in different ways:

~~~clojure
(time
 (count
  (map download SEQ)))
;; Elapsed time: 1102802.057709 msecs

(time
 (count
  (pmap download SEQ)))
;; Elapsed time: 44213.30375 msecs

(time
 (count
  (v3/map download SEQ)))
;; Elapsed time: 11124.417959 msecs

(time
 (count
  (v3/pmap 512 download SEQ)))
;; Elapsed time: 11090.514792 msecs
~~~

The standard `map` function lasts forever because it downloads files one by
one. If the file size is 6 megabyes and the rate limit is 500 kbs, it will take
12 secods to fetch it. Therefore, downloading 100 files takes 12 sec * 100 =
1200 seconds = 20 minutes.

The `pmap` function behaves better of course as it parallels jobs. My laptop has
got 12 CPUs meaning that, theoretically, it can download 14 files simultaneously
(pmap window size = CPU + 2). Above, downloading 100 files takes 44 seconds.

Now, the two `map` functions powered with virtual threads. One soon as one
virtual thread emits a blocking IO call, its stack trace replaced with a stack
trace of another thread that has just woken up from blocking IO. In our case,
all 100 files get downloaded in parallel, and the final time is 12 seconds. It
took as longs as to download a single file -- but we got 100 files.

A quick example of breaking `ulimit` constrain. 100 (files) is less than 1024
(default ulimit) so we're not breaking it. Now imagine we'd like to download
2000 files using virtual thread. This is what will happen:

~~~clojure
(time
 (count
  (v3/map download (range 2000))))

INFO: I/O exception (java.net.SocketException) caught
  when processing request to {}->http://127.0.0.1:8080: Connection reset
... org.apache.http.impl.execchain.RetryExec execute
INFO: Retrying request to {}->http://127.0.0.1:8080
~~~

Other HTTP clients may fail with "too many open connections" error. The right
approach would be to use `v/pmap` with a window size:

~~~clojure
(time
 (count
  (v3/pmap 512 download (range 2000))))
;; Elapsed time: 44684.907583 msecs
~~~

2000 files in 45 seconds! It means, every second were downloading about 44
files.

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
