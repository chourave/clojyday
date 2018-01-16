;; Copyright and license information at end of file

(def +project+ 'clojyday)
(def +version+ "0.1-SNAPSHOT")

(set-env!

 :resource-paths
 #{"src"}

 :source-paths
 #{"test"}

 :exclusions
 '[org.clojure/clojure]

 :dependencies
 '[[clojure.java-time                   "0.3.1"]
   [de.jollyday/jollyday                "0.5.3"]
   [javax.xml.bind/jaxb-api             "2.3.0"        :scope "test"]
   [metosin/boot-alt-test               "0.3.2"        :scope "test"]
   [orchestra                           "2017.11.12-1" :scope "test"]
   [org.clojure/clojure                 "1.9.0"]
   [org.eclipse.persistence/eclipselink "2.7.1"        :scope "test"]])

(task-options!
 pom {:project     +project+
      :version     +version+
      :description ""
      :url         "https://github.com/chourave/clojyday"
      :scm         {:url "https://github.com/chourave/clojyday"}
      :license     {"Apache-2.0" "http://www.apache.org/licenses/LICENSE-2.0"}})

(require
 '[boot.pod :as pod]
 '[metosin.boot-alt-test :refer (alt-test)])

(deftask test-clj
  "Run unit tests"
  []
  (comp
   (watch)
   (alt-test :report 'eftest.report.pretty/report)))

(deftask check-conflicts
  "Verify there are no dependency conflicts."
  []
  (with-pass-thru [_]
    (require '[boot.pedantic :as pedant])
    (let [dep-conflicts (resolve 'pedant/dep-conflicts)]
      (if-let [conflicts (not-empty (dep-conflicts pod/env))]
        (throw (ex-info (str "Unresolved dependency conflicts. "
                             "Use :exclusions to resolve them!")
                        conflicts))
        (println "\nVerified there are no dependency conflicts.")))))

(deftask check-deps
  "Check that dependencies are in good shape"
  []
  (comp
   (show :updates true)
   (check-conflicts)))

;; Copyright 2018 Frederic Merizen
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
;; or implied. See the License for the specific language governing
;; permissions and limitations under the License.