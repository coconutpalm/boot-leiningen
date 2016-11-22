(set-env! :project "com.github.coconutpalm.boot-leiningen" :version "0.0.1"

          :source-paths #{"src" "test"}

          :repositories #(conj % '["jitpack" {:url "https://jitpack.io"}])

          :dependencies '[[org.clojure/clojure         "1.8.0"]
                          [org.clojure/clojurescript   "1.9.293"]

                          [boot/base                   "2.6.0"]
                          [boot/core                   "2.6.0"]

                          [adzerk/bootlaces            "0.1.13"]
                          [adzerk/boot-test            "1.1.2"]
                          [adzerk/boot-cljs            "1.7.228-1"]
                          [adzerk/boot-reload          "0.4.12"]
                          [adzerk/boot-cljs-repl       "0.3.3"]

                          [boot/pod                    "2.6.0"]
                          [boot/aether                 "2.6.0"]
                          [boot/worker                 "2.6.0"]

                          [pandeiro/boot-http          "0.7.3"]
                          [funcool/boot-codeina        "0.1.0-SNAPSHOT"]
                          [seancorfield/boot-new       "0.4.7"]
                          [com.cemerick/piggieback     "0.2.1"]
                          [weasel                      "0.7.0"]
                          [org.clojure/tools.nrepl     "0.2.12"]
                          [jonase/eastwood             "0.2.3"]
                          [tolitius/boot-check         "0.1.3"]
                          [spyscope                    "0.1.6"]
                          [samestep/boot-refresh       "0.1.0"]
                          [metosin/boot-alt-test       "0.1.2"]
                          [adzerk/boot-test            "1.1.0"]
                          [adzerk/boot-jar2bin         "1.1.0"]
                          [nightlight                  "1.2.1"]])


(require '[boot-leiningen.core :refer :all]
         '[funcool.boot-codeina :refer :all])


(task-options!
     apidoc {:title       "Boot-Leiningen"
             :version     (get-env :version)
             :sources     #{"src" "test"}
             :description "Boot and Leiningen utilities with a focus on Leiningen interop from Boot."})
