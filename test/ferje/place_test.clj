;; Copyright and license information at end of file

(ns ferje.place-test

  (:require
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [ferje.jaxb-utils :refer [jaxb-fixture]]
   [ferje.place :as place]
   [ferje.spec-test-utils :refer [instrument-fixture]]
   [ferje.util :as util]
   [clojure.java.io :as io])

  (:import
   (de.jollyday HolidayCalendar)
   (de.jollyday.parameter UrlManagerParameter)
   (java.util Locale)))


;; Fixtures

(use-fixtures :once
  (join-fixtures [jaxb-fixture instrument-fixture]))


;; Basic type predicates

(deftest locale?-test
  (testing "only instances of Locale are locales"
    (is (place/locale? (Locale/FRANCE)))
    (is (not (place/locale? :france)))))


(deftest format?-test
  (is (place/format? :any-format))
  (is (place/format? :xml))
  (is (place/format? :xml-jaxb))
  (is (not (place/format? :random))))


;;



(deftest format-properties-test
  (let [old-hierarchy @#'place/format-hierarchy]
    (with-redefs [place/format-hierarchy (-> (make-hierarchy)
                                             (derive :foo :foo/bar)
                                             (derive :foo/bar :any-format))]
      (testing "with namespaced keyword"
        (is (= :foo/bar
               (-> (doto (UrlManagerParameter. nil nil)
                     (place/set-format! :foo/bar))
                   place/get-format))))
      (testing "with plain keyword"
        (is (= :foo
               (-> (doto (UrlManagerParameter. nil nil)
                     (place/set-format! :foo))
                   place/get-format)))))))


;; Parsing a place

(defn get-calendar-id
  "Prove that we can load HolidayManager that’s at
  least superficially valid by returning its id."
  [cal]
  (-> (place/holiday-manager :xml-jaxb cal)
      (.getCalendarHierarchy) (.getId)))


(deftest holiday-manager-test

  ;; Smoke test: we check that we can load a
  ;; HolidayManager through the various
  ;; means of identifying it.

  (testing "With a locale keyword identifier"
    (is (= "fr" (get-calendar-id :fr))))
  (testing "With a locale string identifier"
    (is (= "fr" (get-calendar-id "fr"))))
  (testing "With a Java locale"
    (is (= "fr" (get-calendar-id Locale/FRANCE))))
  (testing "With a an existing holiday calendar"
    (is (= "fr" (get-calendar-id  (HolidayCalendar/FRANCE)))))
  (testing "With a configuration file URL"
    (is (= "fr" (get-calendar-id (io/resource "holidays/Holidays_fr.xml"))))))


(deftest parse-place-test
  (testing "With a single arument, give holidays for the whole country"
    (let [{::place/keys [manager zones]} (place/parse-place :xml-jaxb :fr)]

      (is (util/string-array? zones))
      (is (nil? (seq zones)))
      (is (= (place/holiday-manager :xml-jaxb :fr) manager))))

  (testing "More arguments are translated into zones"
    (let [{::place/keys [manager zones]} (place/parse-place :xml-jaxb [:fr :br])]

      (is (util/string-array? zones))
      (is (= ["br"]  (seq zones)))
      (is (= (place/holiday-manager :xml-jaxb :fr) manager)))))


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
