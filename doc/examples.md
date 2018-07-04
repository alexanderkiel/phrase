# Phrase Examples
The following examples assume that you have required `clojure.spec` and `phrase` in a REPL session like so:
```Clojure
(require '[clojure.spec.alpha :as s])
(require '[phrase.alpha :as p])
```

### Required Keys
This example demonstrates how to phrase the problem of a required spec key that is missing:
```Clojure
(s/def ::id string?)
(s/def ::required-params (s/keys :req [::id]))

(p/defphraser #(contains? % key)
  [_ _ key]
  (format "Missing %s." (name key)))

(p/phrase-first {} ::required-params {})
;;=> "Missing id."

;; Note: This phraser depends on an internal `clojure.spec` implementation detail
;;  (the fact that spec uses `contains?` for verifying key membership) and it is
;;  possible that this detail could change in the future.
```

### Retrieving Problem Details in Phraser
This example demonstrates how to retrieve the problem key from within a phraser:
```Clojure
(s/def ::first-name (s/and string? #(not (empty? %))))

(p/defphraser #(not (empty? %))
  [_ problem]
  (let [msg (str (-> problem :via last name) " must not be empty.")]
    {:message msg
     :code "empty-string"}))

(p/phrase-first {} ::first-name "")
;;=> {:message "first-name must not be empty.", :code "empty-string"}
```

We can also retrieve the path to the problem key (e.g. in a nested map):
```Clojure

(s/def ::first-name (s/and string? #(not (empty? %))))
(s/def ::person (s/keys :req-un [::first-name]))
(s/def ::location (s/keys :opt-un [::person]))

(p/defphraser #(not (empty? %))
  [_ problem]
  (let [via (:via problem)
        path (map name via)
        msg (str (last path) " must not be empty.")]
    {:message msg
     :path path
     :code "empty-string"}))

(p/phrase-first {} ::location {:person {:first-name ""}})
;;=> {:message "first-name must not be empty.",
;;    :path ("location" "person" "first-name"),
;;    :code "empty-string"}
```
