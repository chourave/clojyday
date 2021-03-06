;; Copyright and license information at end of file

(ns ferje.util-test

  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ferje.spec-test-utils :refer [instrument-fixture]]
   [ferje.util :as util]
   [orchestra.spec.test :refer [instrument]]))


;; Fixtures

(use-fixtures :once instrument-fixture)


;; Basic type predicates

(deftest string-array?-test
  (testing "only instancs of String[] are string arrays"
    (is (util/string-array? (into-array ["blah"])))
    (is (not (util/string-array? ["bli"])))
    (is (not (util/string-array? (object-array ["blah"]))))))


;; Utility functions

(deftest $-test
  (testing "$ is just function application"
    (is (= 3 (util/$ inc 2)))
    (is (= "3" (util/$ str 3)))))


;; String manipulation

(deftest lowercase?-test
  (is (util/lowercase? "abc"))
  (is (util/lowercase? "abc-def"))
  (is (util/lowercase? "/$+*"))
  (is (not (util/lowercase? "B"))))



(deftest uppercase?-test
  (is (util/uppercase? "ABC"))
  (is (util/uppercase? "ABC-DEF"))
  (is (util/uppercase? "/$+*"))
  (is (not (util/uppercase? "b"))))


(deftest kebab->camel-test
  (is (= "abacaba" (util/kebab->camel "abacaba")))
  (is (= "abaCaba" (util/kebab->camel "aba-caba"))))


(deftest camel->kebab-test
  (is (= "abacaba" (util/camel->kebab "abacaba")))
  (is (= "aba-caba" (util/camel->kebab "abaCaba")))
  (is (= "aba-caba" (util/camel->kebab "AbaCaba"))))


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
