(ns boot-leiningen.core
  (:import [java.io PushbackReader ByteArrayInputStream InputStreamReader]))


(defn string-reader
  [s]
  (-> s (.getBytes) (ByteArrayInputStream.) (InputStreamReader.) (PushbackReader.)))


(defn forms
  "Return a lazy seq of unevaluated forms in the specified PushbackReader."
  [reader]
  (let [form (read {:eof ::eof} reader)]
    (if (= ::eof form)
      nil
      (lazy-seq (cons form (forms reader))))))


(defn project-clj
  "Return the (defproject) form from a project.clj file.  Evals all forms it
  finds before the defproject form in the file so that any constants defined
  earlier will be available for the defproject form."
  []
  (loop [project-forms (-> "project.clj" slurp string-reader forms)]
    (let [form (first project-forms)]
      (cond
        (and (seq? form)
             (= "defproject" (str (first form)))) form
        (= nil form)                              (throw (IllegalStateException. "No defproject form found."))
        :else                                     (do (eval form)
                                                      (recur (rest project-forms)))))))


(defmacro unq
  "If a token needs to be unquoted, resolve it's value."
  [val] `~val)


;; Build environment
(set-env! :repositories #(conj % '["jitpack" {:url "https://jitpack.io"}])

          :dependencies '[[org.clojure/clojure         "1.8.0"     :scope "provided"]
                          [org.clojure/clojurescript   "1.9.229"   :scope "provided" :classifier "aot"]
                          [seancorfield/boot-new       "0.4.7"] ; FIXME: Need a :scope here?
                          [boot/core                   "2.6.0"     :scope "provided"]
                          [adzerk/bootlaces            "0.1.13"    :scope "test"]
                          [adzerk/boot-test            "1.1.0"     :scope "test"]
                          [funcool/boot-codeina        "0.1.0-SNAPSHOT" :scope "test"]
                          [adzerk/boot-cljs            "1.7.228-1" :scope "test"]
                          [pandeiro/boot-http          "0.7.3"     :scope "test"]
                          [adzerk/boot-reload          "0.4.12"    :scope "test"]
                          [adzerk/boot-cljs-repl       "0.3.3"     :scope "test"]
                          [com.cemerick/piggieback     "0.2.1"     :scope "test"]
                          [weasel                      "0.7.0"     :scope "test"]
                          [org.clojure/tools.nrepl     "0.2.12"    :scope "test"]
                          [jonase/eastwood             "0.2.3"     :scope "test"]
                          [tolitius/boot-check         "0.1.3"     :scope "test"]
                          [spyscope                    "0.1.6"     :scope "test"]
                          [samestep/boot-refresh       "0.1.0"     :scope "test"]
                          [metosin/boot-alt-test       "0.1.2"     :scope "test"]
                          [adzerk/boot-test            "1.1.0"     :scope "test"]
                          [adzerk/boot-jar2bin         "1.1.0"     :scope "test"]
                          [nightlight                  "1.0.0"     :scope "test"]])


(require '[clojure.string :as str]
         '[adzerk.boot-test :refer :all]
         '[metosin.boot-alt-test :refer [alt-test]]
         '[adzerk.boot-reload :refer [reload]]
         '[adzerk.boot-jar2bin :refer :all]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
         '[bultitude.core :as b]
         '[tolitius.boot-check :as check]
         '[spyscope.core :refer :all]
         '[samestep.boot-refresh :refer [refresh]]
         '[funcool.boot-codeina :refer :all]
         '[clojure.pprint :refer [pprint]]
         '[nightlight.boot :refer [night]])


(deftask from-lein
  "Take this project's metadata from a Leiningen project.clj."
  []

  (let [project   (project-clj)
        lein-proj (merge {:project (str/replace (str (second project)) "/" ".")
                          :version (nth project 2)}
                         (->> project
                              (drop 3)
                              (partition 2)
                              (map (fn [[k v]] [k (unq v)]))
                              (into {})))]

    (merge-env! :repositories (:repositories lein-proj))

    (set-env!
     :project        (:project lein-proj)
     :certificates   (:certificates lein-proj)
     :source-paths   (or (:source-paths lein-proj) #{"src" "test"})
     :target-path    (or (:target-path lein-proj) "target")
     :resource-paths (or (:resource-paths lein-proj) #{"resources" "src"})
     :dependencies   (into (:dependencies lein-proj)
                           `(get-env :dependencies)))

    (require '[adzerk.bootlaces :refer :all])
    (require '[adzerk.boot-test :refer :all])

    ((resolve 'bootlaces!) (:version lein-proj))

    (task-options!
     repl   (:repl-options lein-proj {})
     aot    (let [aot (:aot lein-proj)
                  all? (or (nil? aot) (= :all aot))
                  ns (when-not all? (set aot))]
              {:namespace ns
               :all all?})
     jar    (conj {}
                  (when (:main lein-proj)     [:main (:main lein-proj)])
                  (if   (:jar-name lein-proj) [:file (:jar-name lein-proj)]
                                              [:file (str (:project lein-proj) ".jar")]))
     bin    (conj {:output-dir "bin"}
                  (when (:jvm-opts lein-proj) [:jvm-opt (:jvm-opts lein-proj)]))
     apidoc {:title       (:project lein-proj)
             :version     (:version lein-proj)
             :sources     (get-env :source-paths)
             :description (:description lein-proj "")}
     pom    {:project     (symbol (:project lein-proj))
             :version     (:version lein-proj)
             :description (:description lein-proj "")
             :url         (:url lein-proj "")
             :scm         (:scm lein-proj "")
             :license     (get lein-proj :license {"EPL" "http://www.eclipse.org/legal/epl-v10.html"})}))
  identity)


(deftask repls
  "From-Lein: Start an nrepl server with watch/refresh support."
  []
  (comp
   (from-lein)
   (repl :server true)
   (watch)
   (refresh)
   (notify :visual :audible)))


(deftask replc
  "Run an nrepl client."
  []
  (repl :client true))


(deftask lint
  "From-Lein: Reveal lint in the codebase."
  []
  (comp
   (from-lein)
   (check/with-yagni)
   (check/with-eastwood)
   (check/with-kibit)
   (check/with-bikeshed)))


(deftask docs
  "From-Lein: Generate API docs"
  []
  (from-lein)
  (apidoc))


(deftask print-boot-deps
  "A simple task to print the dependencies that Boot sees.  Must be
  composed with (from-lein) in order to print dependencies slurped
  in from project.clj."
  []
  (pprint (get-env :dependencies))
  identity)


(deftask print-deps
  "From-Lein: Print dependencies, including those slurped in from project.clj"
  []
  (comp
   (from-lein)
   (print-boot-deps)))


(deftask testing
  "From-Lein: Watch the file system and run tests on the current project.  Starts
  an nrepl server and a nightlight editor for interaction with the running
  application.  Automatically reloads namespaces and re-runs tests when files
  change."
  []
  (comp
   (from-lein)
   (repl :server true)
   (night "--port" "0")
   (watch)
   (refresh)
   (alt-test)  ; Only run changed and dependent tests
   (speak)
   (notify :visual :audible)))


(deftask test+install
  "From-Lein: Run tests, build a jar, and install it into the local repo."
  []
  (comp
   (from-lein)
   (test)
   (speak)
   (notify :visual :audible)
   (pom)
   (jar)
   (install)))


(deftask uberbin
  "From-Lein: Run tests, and build a direct-executable, aot'd uberjar."
  []
  (comp
   (from-lein)
   (aot)
   (test)
   (speak)
   (notify :visual :audible)
   (pom)
   (uber)
   (jar)
   (bin)))
