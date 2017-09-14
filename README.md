# Phrase

[![Build Status](https://travis-ci.org/alexanderkiel/phrase.svg?branch=master)](https://travis-ci.org/alexanderkiel/phrase)

Clojure(Script) library for phrasing [spec][2] problems. Phrasing refers to converting to human readable messages.

This library can be used in various scenarios but it's primary focus is on form validation. I talked about [Form Validation with Clojure Spec][1] in Feb 2017 and Phrase is the library based on this talk.

## Install

To install, just add the following to your project dependencies:

```clojure
[phrase "0.1-alpha1"]
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

## License

Copyright © 2017 Alexander Kiel

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: <https://www.slideshare.net/alexanderkiel/form-validation-with-clojure-spec>
[2]: <https://clojure.org/about/spec>
