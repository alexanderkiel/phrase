(ns phrase.alpha-test
  (:require
    #?@(:clj
        [[clojure.spec.alpha :as s]
         [clojure.test :refer :all]
         [phrase.alpha :refer [defphraser phrase-first remove-default!]]]
        :cljs
        [[cljs.spec.alpha :as s]
         [cljs.test :refer-macros [deftest are is testing]]
         [goog.string :refer [format]]
         [goog.string.format]
         [phrase.alpha :refer [phrase-first remove-default!] :refer-macros [defphraser]]])
         [clojure.string :as str]))

(s/def ::age
  int?)

(defphraser int?
  [_ _]
  "Please enter an integer.")

(deftest int-test
  (is (= "Please enter an integer." (phrase-first {} ::age "42"))))

(s/def ::password
  #(<= 8 (count %)))

(defphraser #(<= min-length (count %))
  [_ {:keys [val]} min-length]
  (format "Please use at least %s chars." min-length))

(deftest password-test
  (is (= "Please use at least 8 chars." (phrase-first {} ::password "1234"))))

(s/def ::advanced-password
  #(<= 8 (count %) 256))

(defphraser #(<= min-length (count %) max-length)
  [_ {:keys [val]} min-length max-length]
  (let [args (if (< (count val) min-length)
               ["less" "minimum" min-length]
               ["more" "maximum" max-length])]
    (apply format "You entered %s chars which is %s than the %s length of %s chars."
           (count val) args)))

(deftest advanced-password-test
  (is (= "You entered 4 chars which is less than the minimum length of 8 chars."
         (phrase-first {} ::advanced-password "1234")))

  (is (= "You entered 257 chars which is more than the maximum length of 256 chars."
         (phrase-first {} ::advanced-password (apply str (repeat 257 "x"))))))

(deftest default-test
  (testing "The default is to return nil"
    (is (nil? (phrase-first {} string? 1))))

  (testing "The user can define it's own default"
    (defphraser :default
      [_ _]
      "Unknown problem.")
    (is (= "Unknown problem." (phrase-first {} string? 1)))
    (remove-default!)))

(s/def ::year
  pos-int?)

(defphraser pos-int?
  {:via [::year]}
  [_ _]
  "The year has to be a positive integer.")

(defphraser pos-int?
  [_ _]
  "Please enter a positive integer.")

(deftest year-test
  (is (= "The year has to be a positive integer."
         (phrase-first {} ::year "1942"))))

(s/def ::identifier
  #(re-matches #"[a-z][a-z0-9]*" %))

(defphraser #(re-matches re %)
  [_ _ re]
  (format "Invalid identifier! Needs to match %s."
          #?(:clj (str "/" re "/") :cljs re)))

(deftest identifier-test
  (is (= "Invalid identifier! Needs to match /[a-z][a-z0-9]*/."
         (phrase-first {} ::identifier "0"))))

(def via-test? int?)

(s/def ::via-test
  via-test?)

(defphraser via-test?
  [_ {:keys [via]}]
  via)

(deftest via-test
  (testing "via is kept"
    (is (= [::via-test] (phrase-first {} ::via-test "")))))
