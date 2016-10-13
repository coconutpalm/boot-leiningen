;; Generic build.boot for lein projects
;;
;; * Configures itself from Leiningen's project.clj
;; * Enables auto-test on file save with `boot testing'
;; * Enables nightlight web editor and repl during auto-test task

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


(deftask repls
  "From-Lein: Start an nrepl server with watch/refresh support."
  []
  (comp
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
   (check/with-yagni)
   (check/with-eastwood)
   (check/with-kibit)
   (check/with-bikeshed)))


(deftask print-boot-deps
  "A simple task to print the dependencies that Boot sees.  Must be
  composed with (from-lein) in order to print dependencies slurped
  in from project.clj."
  []
  (pprint (get-env :dependencies))
  identity)


(deftask dev
  "Watch the file system and run tests on the current project.  Starts
  an nrepl server and a nightlight editor for interaction with the running
  application.  Automatically reloads namespaces and re-runs tests when files
  change."
  []
  (comp
   (repl :server true)
   (night "--port" "0")
   (watch)
   (refresh)
   (test)
   (speak)
   (notify :visual :audible)))


(deftask test+install
  "Run tests, build a jar, and install it into the local repo."
  []
  (comp
   (test)
   (speak)
   (notify :visual :audible)
   (pom)
   (jar)
   (install)))


