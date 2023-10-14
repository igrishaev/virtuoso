(defproject com.github.igrishaev/virtuoso "0.1.0-SNAPSHOT"

  :java-cmd
  "/Users/ivan/work/jdk-21.jdk/Contents/Home/bin/java"

  :description
  "FIXME: write description"

  :url
  "http://example.com/FIXME"

  :license
  {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
   :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies
  [[org.clojure/clojure "1.11.1"]]

  :profiles
  {:dev
   {:source-paths ["dev/src"]
    :dependencies [[clj-http "3.12.0"]
                   [cheshire "5.10.0"]]}
   :uberjar
   {:aot :all
    :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
