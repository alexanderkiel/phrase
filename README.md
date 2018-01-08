# Phrase

[![Build Status](https://travis-ci.org/alexanderkiel/phrase.svg?branch=master)](https://travis-ci.org/alexanderkiel/phrase)
[![Dependencies Status](https://versions.deps.co/alexanderkiel/phrase/status.svg)](https://versions.deps.co/alexanderkiel/phrase)
[![Downloads](https://versions.deps.co/alexanderkiel/phrase/downloads.svg)](https://versions.deps.co/alexanderkiel/phrase)

Clojure(Script) library for phrasing [spec][2] problems. Phrasing refers to converting to human readable messages.

This library can be used in various scenarios but it's primary focus is on form validation. I talked about [Form Validation with Clojure Spec][1] in Feb 2017 and Phrase is the library based on this talk.

The main idea of this library is to dispatch on spec problems and let you generate human readable messages for individual and whole classes of problems. Phrase doesn't try to generically generate messages for all problems like [Expound][3] does. The target audience for generated messages are end-users of an application not developers.

## Install

To install, just add the following to your project dependencies:

```clojure
[phrase "0.2-alpha1"]
```

## Usage

Assuming you like to validate passwords which have to be strings with at least 8 chars, a spec would be:

```clojure
(require '[clojure.spec.alpha :as s])
(require '[clojure.string :as str])

(s/def ::password
  #(<= 8 (count %)))
```

executing

```clojure
(s/explain-data ::password "1234")
```

will return one problem:

```clojure
{:path [],
 :pred (clojure.core/fn [%] (clojure.core/<= 8 (clojure.core/count %))),
 :val "",
 :via [:user/password],
 :in []}
```

Phrase helps you to convert such problem maps into messages for your end-users which you define. Phrase doesn't generate messages in a generic way.

The main discriminator in the problem map is the predicate. Phrase provides a way to dispatch on that predicate in a quite advanced way. It allows to substitute concrete values with symbols which bind to that values. In our case we would like to dispatch on all predicates which require a minimum string length regardless of the concrete boundary. In Phrase you can define a phraser:

```clojure
(require '[phrase.alpha :refer [defphraser]])

(defphraser #(<= min-length (count %))
  [_ _ min-length]
  (format "Please use at least %s chars." min-length))
``` 

the following code:

```clojure
(require '[phrase.alpha :refer [phrase-first]])

(phrase-first {} ::password "1234")
```

returns the desired message:

```clojure
"Please use at least 8 chars."
```

### The defphraser macro

In it's minimal form, the defphraser macro takes a predicate and an argument vector of two arguments, a context and the problem:

```clojure
(defphraser int?
  [context problem]
  "Please enter an integer.")
``` 

The context is the same as given to `phrase-first` it can be used to generate I18N messages. The problem is the spec problem which can be used to retrieve the invalid value for example.

In addition to the minimal form, the argument vector can contain one or more trailing arguments which can be used in the predicate to capture concrete values. In the example before, we captured `min-length`:

```clojure
(defphraser #(<= min-length (count %))
  [_ _ min-length]
  (format "Please use at least %s chars." min-length))
``` 

In case the predicated used in a spec is `#(<= 8 (count %))`, `min-length` resolves to 8.

Combined with the invalid value from the problem, we can build quite advanced messages:

```clojure
(s/def ::password
  #(<= 8 (count %) 256))
  
(defphraser #(<= min-length (count %) max-length)
  [_ {:keys [val]} min-length max-length]
  (let [args (if (< (count val) min-length)
               ["less" "minimum" min-length]
               ["more" "maximum" max-length])]
    (apply format "You entered %s chars which is %s than the %s length of %s chars."
           (count val) args)))
           
(phrase-first {} ::password "1234")
;;=> "You entered 4 chars which is less than the minimum length of 8 chars."

(phrase-first {} ::password (apply str (repeat 257 "x"))) 
;;=> "You entered 257 chars which is more than the maximum length of 256 chars."          
``` 

Besides dispatching on the predicate, we can additionally dispatch on `:via` of the problem. In `:via` spec encodes a path of spec names (keywords) in which the predicate is located. Consider the following:

```clojure
(s/def ::year
  pos-int?)

(defphraser pos-int?
  [_ _]
  "Please enter a positive integer.")

(defphraser pos-int?
  {:via [::year]}
  [_ _]
  "The year has to be a positive integer.")

(phrase-first {} ::year "1942")
;;=> "The year has to be a positive integer."
```

Without the additional phraser with the `:via` specifier, the message `"Please enter a positive integer."` would be returned. By defining a phraser with a `:via` specifier of `[::year]`, the more specific message `"The year has to be a positive integer."` is returned.

### Default Phraser

It's certainly useful to have a default phraser which is used whenever no matching phraser is found. You can define a default phraser using the keyword `:default` instead of a predicate.

```clojure
(defphraser :default
  [_ _]
  "Invalid value!")
```

### More Complex Example

If you like to validate more than one thing, for example correct length and various regexes, I suggest that you build a spec using `s/and` as opposed to building a big, complex predicate which would be difficult to match.

In this example, I require a password to have the right length and contain at least one number, one lowercase letter and one uppercase letter. For each requirement, I have a separate predicate.

```clojure
(s/def ::password
  (s/and #(<= 8 (count %) 256)
         #(re-find #"\d" %)
         #(re-find #"[a-z]" %)
         #(re-find #"[A-Z]" %)))

(defphraser #(<= lo (count %) up)
  [_ {:keys [val]} lo up]
  (format "Length has to be between %s and %s but was %s." 
          lo up (count val)))

;; Because Phrase replaces every concrete value like the regex, we can't match
;; on it. Instead, we define only one phraser for `re-find` and use a case to 
;; build the message.
(defphraser #(re-find re %)
  [_ _ re]
  (format "Has to contain at least one %s."
          (case (str/replace (str re) #"/" "")
            "\\d" "number"
            "[a-z]" "lowercase letter"
            "[A-Z]" "uppercase letter")))

(phrase-first {} ::password "a")
;;=> "Length has to be between 8 and 256 but was 1."

(phrase-first {} ::password "aaaaaaaa")
;;=> "Has to contain at least one number."

(phrase-first {} ::password "AAAAAAA1")
;;=> "Has to contain at least one lowercase letter."

(phrase-first {} ::password "aaaaaaa1")
;;=> "Has to contain at least one uppercase letter."

(s/valid? ::password "aaaaaaA1")
;;=> true
```

### Phrasing Problems

The main function to phrase problems is `phrase`. It takes the problem directly. There is a helper function called `phrase-first` which does the whole thing. It calls `s/explain-data` on the value using the supplied spec and phrases the first problem, if there is any. However, you have to use `phrase` directly if you like to phrase more than one problem. The library doesn't contain a `phrase-all` function because it doesn't know how to concatenate messages. 

### Kinds of Messages

Phrase doesn't assume anything about messages. Messages can be strings or other things like [hiccup][4]-style data structures which can be converted into HTML later. Everything is supported. Just return it from the `defphraser` macro. Phrase does nothing with it.

## Related Work

* [Expound][3] - aims to generate more readable messages as `s/explain`. The audience are developers not end-users.

## License

Copyright © 2017 Alexander Kiel

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: <https://www.slideshare.net/alexanderkiel/form-validation-with-clojure-spec>
[2]: <https://clojure.org/about/spec>
[3]: <https://github.com/bhb/expound>
[4]: <https://github.com/weavejester/hiccup>
