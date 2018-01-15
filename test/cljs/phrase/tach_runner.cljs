(ns phrase.tach-runner
  (:require
    [cljs.test :refer-macros [run-tests]]
    [phrase.alpha-test]))

(run-tests 'phrase.alpha-test)
