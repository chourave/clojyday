;; Copyright and license information at end of file

(ns ferje.config.edn-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.walk :refer [prewalk]]
   [ferje.core :as ferje]
   [ferje.config.edn :as edn-config]
   [ferje.place :as place]
   [ferje.spec-test-utils :refer [instrument-fixture]]
   [java-time :as time])
  (:import
   (de.jollyday.config ChristianHoliday ChristianHolidayType ChronologyType Configuration
                       EthiopianOrthodoxHoliday EthiopianOrthodoxHolidayType
                       Fixed FixedWeekdayBetweenFixed FixedWeekdayInMonth
                       FixedWeekdayRelativeToFixed HebrewHoliday HinduHoliday
                       HinduHolidayType Holidays HolidayType IslamicHoliday
                       IslamicHolidayType Month MovingCondition RelativeToEasterSunday
                       RelativeToFixed RelativeToWeekdayInMonth Weekday When Which With)))


;; Fixtures

(use-fixtures :once instrument-fixture)


;;

(deftest format?-test
  (is (place/format? :edn)))


(deftest sort-map-test
  (is (= "{:month :may, :day 1}"
         (-> {:day 1, :month :may}
             edn-config/sort-map
             pr-str)))
  (is (thrown-with-msg? Exception #"Unhandled keys :foo"
                        (edn-config/sort-map {:foo 1}))))


(deftest place-integration-test
  (testing "Use a map literal configuration"
    (is (ferje/holiday?
         :edn {:description "blah", :hierarchy :bl
               :holidays    [{:holiday :fixed
                              :month   :july
                              :day     14}]}
         (time/local-date 2017 7 14)
         :any-holiday))))


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
