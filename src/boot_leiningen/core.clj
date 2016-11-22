(ns boot-leiningen.core
  (:refer-clojure :exclude [test])
  (:require [boot.core :refer :all]
            [boot.pod :as pod]
            [boot.task.built-in :refer :all]
            [adzerk.boot-test :refer :all]
            [adzerk.boot-reload :refer [reload]]

            [clojure.java.io :as io]
            [clojure.repl :as repl]
            [clojure.test :as t]

            [clojure.string :as str]
            [adzerk.boot-jar2bin :refer :all]
            [adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
            [bultitude.core :as b]
            [tolitius.boot-check :as check]
            [spyscope.core :refer :all]
            [samestep.boot-refresh :refer [refresh]]
            [funcool.boot-codeina :refer :all]
            [clojure.pprint :refer [pprint]]
            [nightlight.boot :refer [night]])
  (:import [java.io PushbackReader ByteArrayInputStream InputStreamReader]))



(defn inject-env
  "Used by build.boot to inject the environment into this namespace."
  [env-fn] (env-fn))


(defn doc-str
  "Return the doc string for symbol"
  [symbol]
  (with-out-str (repl/doc symbol)))


(defn string-reader
  "Returns a PushbackReader containing the specified string"
  [s]
  (-> s (.getBytes) (ByteArrayInputStream.) (InputStreamReader.) (PushbackReader.)))


(defmacro unq
  "If a token needs to be unquoted, resolve its value, else return the token itself."
  [val] `~val)


(t/with-test

  (defn forms
    "Return a lazy seq of unevaluated forms in the specified PushbackReader."
    [reader]
    (let [form (read {:eof ::eof} reader)]
      (if (= ::eof form)
        nil
        (lazy-seq (cons form (forms reader))))))

  (t/testing "Whitespace returns an empty seq"
    (t/is (empty? (forms (string-reader "")))))

  (t/testing "A comment returns an empty seq"
    (t/is (empty? (forms (string-reader ";Just a comment")))))

  (t/testing "One form"
    (t/is (= 1 (count (forms (string-reader "(def i 1)"))))))

  (t/testing "Two forms"
    (let [fs (forms (string-reader "(def i 1) (+ i 3)"))]
      (t/is (= 2 (count fs)))
      (t/is (= 4 (do (eval (first fs)) (eval (second fs))))))))


(defn- test-project [] (str (System/getProperty "user.dir") "/resources/project.clj"))
(defn- get-var [v] (some-> (ns-resolve *ns* 'version) var-get))


(t/with-test
    (defn project-clj
      "Return the (defproject) form from a project.clj file.  Evals all forms it
      finds before the defproject form in the file so that any constants defined
      earlier will be available for the defproject form."
      ([p]
       (loop [project-forms (-> p slurp string-reader forms)]
         (let [form (first project-forms)]
           (cond
             (and (seq? form)
                  (= "defproject" (str (first form)))) form
             (= nil form)                              (throw (IllegalStateException. "No defproject form found."))
             :else                                     (do (eval form)
                                                           (recur (rest project-forms)))))))
      ([] (project-clj "project.clj")))

    (let [prj (project-clj (test-project))]
      (t/testing "Evaluates forms before the (defproject) form"
        (t/is (= "1.8.0" (get-var 'version))))

      (t/testing "Returns the defproject form as a seq"
        (t/is (seq? prj))
        (t/is (= "defproject" (str (first prj))))))

    (t/testing "Throws IllegalStateException if defproject cannot be found."
      (t/is (thrown? IllegalStateException (project-clj (string-reader "(def i 3)"))))))


(t/with-test
  (defn- lein-proj
    "Return Leiningen metadata map from a project.clj file."
    [project-file]
    (let [project (project-clj project-file)]
      (merge  {:project (str (second project))
               :version (nth project 2)}
              (->> project
                   (drop 3)
                   (partition 2)
                   (map (fn [[k v]] [k (unq v)]))
                   (into {})))))

  (let [result-map (lein-proj (test-project))]
    (t/testing "Parses the project name and version"
      (t/is (= "com.github.shopsmart/clj-foundation" (:project result-map)))
      (t/is (= "0.9.16" (:version result-map))))

    (t/testing "Parses the defproject body into metadata map"
      (t/is (= "https://github.com/shopsmart/clj-foundation" (:url result-map))))))


(deftask from-lein
  "Merge a Leiningen project.clj file's metadata into this project's boot environment."
  [p project VAL str "The Leiningen project.clj file to load.  Defaults to project.clj in the current directory."]
  (let [project   (project-clj (:project *opts* "project.clj"))
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
             :sources     (or (:source-paths lein-proj) #{"src" "test"})
             :description (:description lein-proj "")}
     pom    {:project     (symbol (:project lein-proj))
             :version     (:version lein-proj)
             :description (:description lein-proj "")
             :url         (:url lein-proj "")
             :scm         (:scm lein-proj "")
             :license     (get lein-proj :license {"EPL" "http://www.eclipse.org/legal/epl-v10.html"})})
   identity))





(deftask check-conflicts
  "Verify there are no dependency conflicts."
  []
  (with-pass-thru fs
    (require '[boot.pedantic :as pedant])
    (let [dep-conflicts (resolve 'pedant/dep-conflicts)]
      (if-let [conflicts (not-empty (dep-conflicts pod/env))]
        (throw (ex-info (str "Unresolved dependency conflicts. "
                             "Use :exclusions to resolve them!")
                        conflicts))
        (println "\nVerified there are no dependency conflicts."))))
  identity)


#_(deftask l-check-conflicts
   "Like check-conflicts, but obtains project metadata from project.clj"
   (comp
    (from-lein)
    (check-conflicts)))


(deftask repls
  "Start an nrepl server with watch/refresh support."
  []
  (comp
   (repl :server true)
   (watch)
   (refresh)
   (notify :visual true :audible true)))


(deftask l-repls
  "Like repls, but obtains project metadata from project.clj"
  []
  (comp
   (from-lein)
   (repls)))


(deftask replc
  "Run an nrepl client."
  []
  (comp
   (repl :client true)
   (watch)
   (refresh)
   (speak)
   (notify :visual true :audible true)))


(deftask l-replc
  "Like replc, but obtains project metadata from project.clj"
  []
  (comp
    (from-lein)
    (replc)))


(deftask lint
  "Reveal lint in the codebase."
  []
  (comp
   (check/with-yagni)
   (check/with-eastwood)
   (check/with-kibit)
   (check/with-bikeshed)))


(deftask l-lint
  "Like lint, but obtains project metadata from project.clj"
  []
  (comp
   (from-lein)
   (lint)))


(deftask print-deps
  "Print the project dependencies that Boot sees."
  []
  (pprint (get-env :dependencies))
  identity)


(deftask l-print-deps
  "Like print-deps, but obtains project metadata from project.clj"
  []
  (comp
   (from-lein)
   (print-deps)))


(deftask testing
  "Watch the file system and run tests on the current project.  Starts
  an nrepl server and a nightlight editor for interaction with the running
  application.  Automatically reloads namespaces, re-runs tests, and
  generates apidoc when files change."
  []
  (comp
   (repl :server true)
   (night "--port" "0")
   (watch)
   (refresh)
   (test)
   (speak)
   (notify :visual true :audible true)
   (apidoc)))


(deftask l-testing
  "Like testing, but obtains project metadata from project.clj"
  []
  (comp
    (from-lein)
    (testing)))


(deftask test+install
  "Run tests, build a jar, and install it into the local repo."
  []
  (comp
   (test)
   (speak)
   (notify :visual true :audible true)
   (pom)
   (jar)
   (speak)
   (notify :visual true :audible true)
   (install)))


(deftask l-test+install
  "Like test+install, but obtains project metadata from project.clj"
  []
  (comp
   (from-lein)
   (test+install)))


(deftask l-apidoc
  "Like apidoc, but obtains project metadata from project.clj"
  []
  (comp
   (from-lein)
   (apidoc)))


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


(deftask l-uberbin
  "Like uberbin, but obtains project metadata from project.clj"
  []
  (comp
   (from-lein)
   (uberbin)))


(t/run-tests)
