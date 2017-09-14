(defproject phrase "0.1.0-SNAPSHOT"
  :description "Clojure(Script) library for phrasing spec problems."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.6.0"
  :pedantic? :abort

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.7"]]

  :profiles
  {:dev
   {:dependencies [[org.clojure/clojure "1.9.0-alpha19"]
                   [org.clojure/clojurescript "1.9.908"]]}}

  :source-paths ["src"]
  :test-paths ["test/cljc"]

  :cljsbuild
  {:builds
   {:test
    {:source-paths ["src" "test/cljc" "test/cljs"]
     :compiler
     {:output-to "out/testable.js"
      :main phrase.runner
      :optimizations :simple
      :process-shim false}}}}

  :clean-targets ["target" "out"]

  :aliases
  {"cljs-tests" ["doo" "nashorn" "test" "once"]
   "all-tests" ["do" "test," "cljs-tests"]})
