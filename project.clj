(defproject phrase "0.4-SNAPSHOT"
  :description "Clojure(Script) library for phrasing spec problems."
  :url "https://github.com/alexanderkiel/phrase"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.6.0"
  :pedantic? :abort

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.10" :exclusions [org.clojure/clojure]]
            [lein-tach "1.0.0"]]

  :profiles
  {:dev
   {:dependencies [[org.clojure/clojure "1.9.0"]
                   [org.clojure/clojurescript "1.10.339"]]}}

  :source-paths ["src"]
  :test-paths ["test/cljc"]

  :cljsbuild
  {:builds
   {:test
    {:source-paths ["src" "test/cljc" "test/cljs"]
     :compiler
     {:output-to "out/testable.js"
      :main phrase.doo-runner
      :optimizations :simple
      :process-shim false}}}}

  :tach
  {:test-runner-ns phrase.tach-runner}

  :clean-targets ["target" "out"]

  :aliases
  {"cljs-nashorn-tests" ["doo" "nashorn" "test" "once"]
   "cljs-phantom-tests" ["doo" "phantom" "test" "once"]
   "cljs-planck-tests" ["tach" "planck" "test"]
   "all-tests" ["do" "test," "cljs-nashorn-tests," "cljs-phantom-tests," "cljs-planck-tests"]
   "lint" ["eastwood" "{:linters [:all] :exclude-linters [:keyword-typos]}"]})
