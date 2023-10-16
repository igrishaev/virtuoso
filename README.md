# Virtuoso

[virtual-threads]: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html

A small wrapper on top of [virtual threads][virtual-threads] introduced in Java
21.

<!-- toc -->

- [About](#about)
- [Installation](#installation)
- [Usage](#usage)
  * [with-executor](#with-executor)
  * [future-via](#future-via)
  * [futures(!)](#futures)
  * [thread](#thread)
  * [pmap(!)](#pmap)
  * [each(!)](#each)
- [Measurements](#measurements)
- [Links and Resources](#links-and-resources)
- [License](#license)

<!-- tocstop -->

## About

## Installation

Lein

~~~clojure
[com.github.igrishaev/virtuoso "0.1.0"]
~~~

Deps/CLI

~~~clojure
{com.github.igrishaev/virtuoso {:mvn/version "0.1.0"}}
~~~

## Usage

First, import the library:

~~~clojure
(require '[virtuoso.core :as v])
~~~

### with-executor

The `with-executor` wraps a block of code binding a new instance of
`VirtualThreadPerTaskExecutor` to the passed symbol:

~~~clojure
(v/with-executor [exe]
  (do-this ...)
  (do-that ...))
~~~

In the example above, the executor is bound to the `exe` symbol. Exiting from
the macro will trigger closing the executor which, in turn, leads to blocking
untill all the tasks sent to it are complete. The `with-executor` macro,
although might be used on your code, is rather a building material for other
macros.

### future-via

The `future-via` macro spaws a new virtual future through a previously open
executor. You can spawn as many futures you want due to the nature of virtual
threads: there might be millions of them.

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
performance suffers due to continuations and moving the stacktrace from the
stack to the heap and back.

### futures(!)

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

Right before you exit the macro, it closes the executor which leads to blicking
until all the tasks are complete.

Pay attention that `deref`-ing a future that failed leads to throwing an
exception. That's why the macoro doesn't dereference the futures for you as it
doens't know how to handle errors. But if you don't care about exception
handling, there a `futures!` macro that does it for you:

~~~clojure
(v/futures!
  (io-heavy-task-1 ...)
  (io-heavy-task-2 ...)
  (io-heavy-task-3 ...))
~~~

The result will be vector of dereferenced values.

### thread

The `thread` macro just spawns and starts a new virtual thread using the
`(Thread/ofVirtual)` call. Threads in Java do not return values; they can only
be `join`-ed or interrupted. Use this macro when you're interested in a `Thread`
object but not the result.

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

### pmap(!)

### each(!)

## Measurements

## Links and Resources

Java 21 new feature: Virtual Threads #RoadTo21
https://www.youtube.com/watch?v=5E0LU85EnTI


Virtual Threads | Oracle Help Center
https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html

## License

Copyright © 2023 Ivan Grishaev

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
