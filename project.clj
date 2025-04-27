(defproject com.github.igrishaev/virtuoso "0.1.1-SNAPSHOT"

  :java-cmd
  ~(System/getenv "JDK21")

  :description
  "A number of trivial wrappers on top of virtual threads"

  :url
  "https://github.com/igrishaev/virtuoso"

  :deploy-repositories
  {"releases" {:url "https://repo.clojars.org" :creds :gpg}}

  :license
  {:name "The Unlicense"
   :url "https://unlicense.org/"}

  :release-tasks
  [["vcs" "assert-committed"]
   ["test"]
   ["change" "version" "leiningen.release/bump-version" "release"]
   ["vcs" "commit"]
   ["vcs" "tag" "--no-sign"]
   ["deploy"]
   ["change" "version" "leiningen.release/bump-version"]
   ["vcs" "commit"]
   ["vcs" "push"]]

  :dependencies
  []

  :profiles
  {:dev
   {:source-paths ["dev/src"]
    :dependencies [[org.clojure/clojure "1.11.1"]
                   [clj-http "3.12.0"]
                   [cheshire "5.10.0"]]}
   :uberjar
   {:aot :all
    :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
