(ns phrase.alpha
  "Public are the functions `phrase`, `phrase-first` and the macro `defphraser`."
  (:require
    #?@(:clj
        [[clojure.core.specs.alpha]]
        :cljs
        [[cljs.analyzer.api]
         [clojure.string :as str]])

         [clojure.spec.alpha :as s]
         [clojure.walk :as walk])
  #?(:cljs
     (:require-macros [phrase.alpha])))

(defn- normalize-pred
  "Retains symbols in pred. Replaces things like numbers with symbols.

  Example: #(<= (count %) 10) gets #(<= (count %) x0)"
  [pred]
  (let [counter (atom -1)
        mappings (atom {})]
    {::normalized-pred
     (walk/postwalk
       #(if-not (or (symbol? %) (sequential? %))
          (let [sym (symbol (str "x" (swap! counter inc)))]
            (swap! mappings assoc (keyword (name sym)) %)
            sym)
          %)
       pred)
     ::mappings @mappings}))

(defn- dispatch*
  [normalized-pred via]
  (cond-> []
    normalized-pred (conj normalized-pred)
    (seq via) (conj ::dispatch-with-via via)))

(defn- dispatch [_ {::keys [normalized-pred via]}]
  (dispatch* normalized-pred via))

(defmulti phrase*
  "Phrases the given problem for human consumption.

  Dispatches on normalized pred and optional via of problem.

  Dispatches in this order:

  * [normalized-pred via]
  * [via]
  * [normalized-pred]
  * []"
  {:arglists '([context problem])}
  dispatch)

;; Realizes the dispatch hierarchy by removing information and calling
;; phrase again.
(defmethod phrase* :default
  [context problem]
  (let [problem (if (contains? problem ::phase)
                  problem
                  (merge problem {::phase ::phase-pred+via
                                  ::saved-pred (::normalized-pred problem)
                                  ::saved-via (::via problem)}))]
    (cond
      ;; retry with simpler via
      (-> problem ::via rest seq)
      (phrase* context (update problem ::via #(-> % rest vec)))
      ;; pred+via -> via
      (= ::phase-pred+via (::phase problem))
      (phrase* context (-> problem
                           (assoc ::phase ::phase-via)
                           (dissoc ::normalized-pred)
                           (assoc ::via (::saved-via problem))))
      ;; via -> pred
      (= ::phase-via (::phase problem))
      (phrase* context (-> problem
                           (assoc ::phase ::phase-pred)
                           (assoc ::normalized-pred (::saved-pred problem))
                           (assoc ::via [])))
      ;; pred -> default
      (= ::phase-pred (::phase problem))
      (phrase* context (-> problem
                           (assoc ::phase ::phase-default)
                           (dissoc ::normalized-pred))))))

(defn- phrase-problem [{:keys [pred via] :as problem}]
  (merge problem (normalize-pred pred) {::via via}))

(defn phrase
  "Takes a context and a clojure.spec problem and dispatches to a phraser.

  Returns the phrasers return value or nil if none was found and no default
  phraser is defined. Dispatches based on :pred and :via of the problem. See
  phraser macro for details."
  [context problem]
  (phrase* context (phrase-problem problem)))

(defn phrase-first
  "Given a spec and a value x, phrases the first problem using context if any.

  Returns nil if x is valid or no phraser was found and no default phraser is
  defined. See phrase for details. Use phrase directly if you want to phrase
  more than one problem."
  [context spec x]
  (some->> (s/explain-data spec x)
           ::s/problems
           first
           (phrase context)))

(defn- unfn [expr]
  (if (and (seq? expr)
           (symbol? (first expr))
           (= "fn*" (name (first expr))))
    (let [[[s] & form] (rest expr)]
      (conj (walk/postwalk-replace {s '%} form) '[%] 'fn))
    expr))

(defn- ->sym
  "Returns a symbol from a symbol or var"
  [x]
  (cond
    (var? x)
    (let [^clojure.lang.Var v x]
      (symbol (str (.name (.ns v)))
              (str (.sym v))))

    (map? x)
    (:name x)

    :else
    x))

#?(:clj
   (defn- dynaload
     [s]
     (let [ns (namespace s)]
       (assert ns)
       (require (symbol ns))
       (let [v (resolve s)]
         (if v
           @v
           (throw (RuntimeException. (str "Var " s " is not on the classpath"))))))))

#?(:clj
   (def ^:private analyzer-resolve (delay (dynaload 'cljs.analyzer.api/resolve))))

#?(:clj
   (defn- resolve' [env sym]
     (if (:ns env)
       (@analyzer-resolve env sym)
       (resolve env sym)))
   :cljs
   (defn- resolve' [env sym]
     (cljs.analyzer.api/resolve env sym)))

(defn- res [env form bindings]
  (cond
    (keyword? form) form
    (symbol? form)
    (if (some #{form} bindings)
      form
      #?(:clj  (or (->> form (resolve' env) ->sym) form)
         :cljs (let [resolved (or (->> form (resolve' env) ->sym) form)
                     ns-name (namespace resolved)]
                 (symbol
                   (if (and ns-name (str/ends-with? ns-name "$macros"))
                     (subs ns-name 0 (- (count ns-name) 7))
                     ns-name)
                   (name resolved)))))
    (sequential? form)
    (walk/postwalk #(if (symbol? %) (res env % bindings) %) (unfn form))
    :else form))

(defn- replace-syms [pred bindings]
  (let [counter (atom -1)
        mappings (atom (zipmap bindings (repeat nil)))]
    {:pred
     (walk/postwalk
       #(if (or (some #{%} bindings) (= '_ %) (not (or (symbol? %) (sequential? %))))
          (let [sym (symbol (str "x" (swap! counter inc)))]
            (when (some #{%} bindings)
              (swap! mappings assoc % (keyword (name sym))))
            sym)
          %)
       pred)
     :mappings @mappings}))

(s/def ::defphraser-arg-list
  (s/and
    vector?
    (s/cat :context-binding-form #?(:clj  :clojure.core.specs.alpha/binding-form
                                    :cljs any?)
           :problem-binding-form #?(:clj  :clojure.core.specs.alpha/binding-form
                                    :cljs any?)
           :capture-binding-forms (s/* #?(:clj  :clojure.core.specs.alpha/local-name
                                          :cljs simple-symbol?)))))

(s/fdef defphraser
  :args (s/cat :pred any?
               :specifiers (s/? map?)
               :args ::defphraser-arg-list
               :body (s/* any?)))

(defmacro defphraser
  "Defines a phraser.

  Takes a predicate with possible capture symbols which have to be also defined
  in the argument vector.

  Pred can be :default in order to define the default phraser which is used if
  no other phraser matches."
  {:arglists '([pred specifiers? [context-binding-form problem-binding-form
                                  & capture-binding-forms] & body])}
  [pred & more]
  (let [specifiers (when (map? (first more)) (first more))
        [[context-binding-form problem-binding-form & capture-binding-forms]
         & body]
        (if specifiers (rest more) more)
        {:keys [via]} specifiers]
    (if (= :default pred)
      (let [dispatch-val (dispatch* nil via)]
        `(defmethod phrase* '~dispatch-val
           [~context-binding-form ~problem-binding-form]
           ~@body))
      (let [{:keys [pred mappings]}
            (if (= :default pred)
              {:pred :default :mappings {}}
              (replace-syms (res &env pred capture-binding-forms)
                            capture-binding-forms))
            dispatch-val (dispatch* pred via)
            problem (gensym "problem")
            binding-forms
            (cond-> [problem-binding-form `(dissoc ~problem ::normalized-pred
                                                   ::mappings ::via)]
              (not-empty mappings)
              (conj {mappings ::mappings} problem))]
        `(defmethod phrase* '~dispatch-val [~context-binding-form ~problem]
           (let ~binding-forms
             ~@body))))))

(defn remove-default!
  "Removes the default phraser."
  []
  (remove-method phrase* []))
