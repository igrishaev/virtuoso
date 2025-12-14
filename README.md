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

## V2 API (deprecated)

TODO

## V1 API (deprecated)

TODO

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
