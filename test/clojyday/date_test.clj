;; Copyright and license information at end of file

(ns clojyday.date-test

  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [compose-fixtures deftest is testing use-fixtures]]
   [clojyday.date :as date]
   [clojyday.spec-test-utils :refer [instrument-fixture]]
   [java-time :as time]))


;; Fixtures

(use-fixtures :once instrument-fixture)


;; Basic type predicates

(deftest local-date?-test
  (testing "Only instances of local-date are a local-date"

    (is (date/local-date? (time/local-date 2003 4 16)))
    (is (not (date/local-date? 2003))
        "A year on its own is not a local-date (although a local-date could be built from one)")))


(deftest local-dateable?-test
  (testing "Values are local-dateable if and only if they can be converted to a local-date"

    (is (date/local-dateable? (time/local-date 2003 4 16))
        "An already built local-date can trivially be converted to a local-date")
    (is (date/local-dateable? 2003)
        "A year can be converted to a local-date")
    (is (not (date/local-dateable? "Hello"))
        "A string cannot be converted to a local-date")))


;; Other predicates

(deftest date-matches-year-month?-test
  (testing "Year and month must both match"

    (is (date/date-matches-year-month?
         (time/local-date 2009 7 18)
         {2009 7}))

    (is (not (date/date-matches-year-month?
              (time/local-date 2009 6 30)
              {2009 7})))))


(deftest date-matches-stake?-test
  (testing "For a year stake, the year must match"
    (is (date/date-matches-stake?
         (time/local-date 2013 12 31)
         (s/conform `date/date-stake 2013)))

    (is (not (date/date-matches-stake?
              (time/local-date 2012 12 31)
              (s/conform `date/date-stake 2013)))))

  (testing "For a mont stake, the year and month must match"
    (is (date/date-matches-stake?
         (time/local-date 2013 8 31)
         (s/conform `date/date-stake {2013 8})))

    (is (not (date/date-matches-stake?
              (time/local-date 2012 7 31)
              (s/conform `date/date-stake {2013 8})))))

  (testing "For something that can be converted to a local-date, the whole local-date must match"
    (is (date/date-matches-stake?
         (time/local-date 2013 12 1)
         (s/conform `date/date-stake (time/local-date 2013 12))))

    (is (not (date/date-matches-stake?
              (time/local-date 2013 12 2)
              (s/conform `date/date-stake (time/local-date 2013 12)))))))


(deftest month?-test
  (testing "A month is specified as a single element map, {year month}"

    (is (date/month? {2007 11}))
    (is (not (date/month? 2007)))
    (is (not (date/month? (time/local-date 2007 11))))))


;; Parsing a date

(deftest parse-month-test
  (testing "A valid month spec is parsed as a local-date"
    (is (= (time/local-date 2018 9)
           (date/parse-month {2018 9}))))

  (testing "Anything else is parsed as nil"
    (is (nil? (date/parse-month 2013)))
    (is (nil? (date/parse-month (time/local-date 2018 9))))))

(deftest parse-boundary-test
  (testing "Parsing a year uses year-adjust"
    (is (= (time/local-date 2222 1 1)
           (date/parse-boundary 2222 :first-day-of-year :last-day-of-month))))

  (testing "Parsing a month uses month-adjust"
    (is (= (time/local-date 2222 11 30)
           (date/parse-boundary {2222 11} :first-day-of-year :last-day-of-month))))

  (testing "Parsing a full date does no adjustment"
    (is (= (time/local-date 3333 2 13)
           (date/parse-boundary (time/local-date 3333 2 13) :first-day-of-year :last-day-of-month)))))

(deftest parse-date-or-interval-test
  (testing "A single integer is a year"
    (is (= #::date{:year 2013}
           (date/parse-date-or-interval 2013))))

  (testing "A single-element map is a {year month}"
    (is (= #::date{:from (time/local-date 2013 12 1)
                       :to (time/local-date 2013 12 31)}
           (date/parse-date-or-interval {2013 12}))))

  (testing "A two-element vector is a [from to]"
    (let [from (time/local-date 2000 1 10)
          to (time/local-date 2010 11 11)]
      (is (= #::date{:from from, :to to}
             (date/parse-date-or-interval [from to]))))

    (is (= #::date{:from (time/local-date 2013 12 1)
                       :to (time/local-date 2014 2 28)}
           (date/parse-date-or-interval [{2013 12} {2014 2}]))))

  (is (= #::date{:from (time/local-date 2013 1 1)
                     :to (time/local-date 2014 12 31)}
         (date/parse-date-or-interval [2013 2014]))))


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
