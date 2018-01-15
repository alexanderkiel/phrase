(ns phrase.alpha-test
  (:require
    #?@(:cljs
        [[goog.string :as gstr]
         [goog.string.format]])

    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.test :refer [deftest are is testing]]
    [phrase.alpha :refer [defphraser phrase-first remove-default!]]))

#?(:cljs (def format gstr/format))

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
  [_ _ min-length]
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

(s/def ::date
  (s/keys :req [::year]))

(defphraser pos-int?
  [_ _]
  "Please enter a positive integer.")

(defphraser pos-int?
  {:via [::year]}
  [_ _]
  "The year has to be a positive integer.")

(deftest via-dispatching-test
  (testing "The default is used for pos-int?"
    (is (= "Please enter a positive integer."
           (phrase-first {} (s/spec pos-int?) -1))))

  (testing "Via ::year is used."
    (is (= "The year has to be a positive integer."
           (phrase-first {} ::year -1))))

  (testing "Via ::year is also used inside ::date."
    (is (= "The year has to be a positive integer."
           (phrase-first {} ::date {::year -1})))))

(s/def ::identifier
  #(re-matches #"[a-z][a-z0-9]*" %))

(defphraser #(re-matches re %)
  [_ _ re]
  (format "Invalid identifier! Needs to match %s."
          #?(:clj (str "/" re "/") :cljs re)))

(deftest identifier-test
  (is (= "Invalid identifier! Needs to match /[a-z][a-z0-9]*/."
         (phrase-first {} ::identifier "0"))))

(s/def ::barcode
  #(re-matches #"[0-9]+" %))

(defphraser #(re-matches #"foo" %)
  {:via [::barcode]}
  [_ _]
  "Invalid barcode.")

(deftest barcode-test
  (testing "Keeping concrete values is possible, but the value itself doesn't matter."
    (is (= "Invalid barcode." (phrase-first {} ::barcode "a")))))

(s/def ::underscore
  #(= "_" %))

(defphraser #(= _ %)
  {:via [::underscore]}
  [_ _]
  "Invalid underscore.")

(deftest underscore-test
  (testing "Keeping concrete values is possible, but the value itself doesn't matter."
    (is (= "Invalid underscore." (phrase-first {} ::underscore "a")))))

(def via-test? int?)

(s/def ::via-test
  via-test?)

(defphraser via-test?
  [_ {:keys [via]}]
  via)

(deftest via-test
  (testing "via is kept"
    (is (= [::via-test] (phrase-first {} ::via-test "")))))

(s/def ::complex-password
  (s/and #(<= 8 (count %) 256)
         #(re-find #"\d" %)
         #(re-find #"[a-z]" %)
         #(re-find #"[A-Z]" %)))

(defphraser #(<= lo (count %) up)
  {:via [::complex-password]}
  [_ {:keys [val]} lo up]
  (format "Length has to be between %s and %s but was %s." lo up (count val)))

(defphraser #(re-find re %)
  [_ _ re]
  (format "Has to contain at least one %s."
          (case (str/replace (str re) #"/" "")
            "\\d" "number"
            "[a-z]" "lowercase letter"
            "[A-Z]" "uppercase letter")))

(deftest complex-password-test
  (testing "length"
    (is (= "Length has to be between 8 and 256 but was 1."
           (phrase-first {} ::complex-password "a"))))

  (testing "number"
    (is (= "Has to contain at least one number."
           (phrase-first {} ::complex-password "aaaaaaaa"))))

  (testing "lowercase"
    (is (= "Has to contain at least one lowercase letter."
           (phrase-first {} ::complex-password "AAAAAAA1"))))

  (testing "uppercase"
    (is (= "Has to contain at least one uppercase letter."
           (phrase-first {} ::complex-password "aaaaaaa1"))))

  (testing "valid"
    (is (s/valid? ::complex-password "aaaaaaA1"))))
