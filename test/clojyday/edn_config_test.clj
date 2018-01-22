;; Copyright and license information at end of file

(ns clojyday.edn-config-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.xml :as xml]
   [clojyday.edn-config :as edn-config]
   [clojyday.spec-test-utils :refer [instrument-fixture]]))

;; Fixtures

(use-fixtures :once instrument-fixture)


(def testing-xml
  {:tag   :tns:Gang
   :attrs {:name    "the Daltons"
           :type    "family"
           :subType "brothers"}
   :content
   [{:tag     :tns:GangMember
     :attrs   {:name "Joe" :family "Dalton"}
     :content []}
    {:tag     :tns:GangMember
     :attrs   {:name "Jack" :family "Dalton"}
     :content []}
    {:tag     :tns:Pet
     :attrs   {:name "Rantanplan"}
     :content []}]})

(deftest attribute-test
  (is (= "the Daltons"
         (edn-config/attribute testing-xml :name))))

(deftest elements-test
  (is (= [{:tag     :tns:GangMember
           :attrs   {:name "Joe" :family "Dalton"}
           :content []}
          {:tag     :tns:GangMember
           :attrs   {:name "Jack" :family "Dalton"}
           :content []}]
         (edn-config/elements testing-xml :GangMember)))
  (is (not (edn-config/elements testing-xml :inexistent))))

(deftest element-test
  (is (= {:tag     :tns:Pet
          :attrs   {:name "Rantanplan"}
          :content []}
         (edn-config/element testing-xml :Pet))))

(deftest kebab->camel-test
  (is (= "abacaba" (edn-config/kebab->camel "abacaba")))
  (is (= "abaCaba" (edn-config/kebab->camel "aba-caba"))))

(deftest camel->kebab-test
  (is (= "abacaba" (edn-config/camel->kebab "abacaba")))
  (is (= "aba-caba" (edn-config/camel->kebab "abaCaba")))
  (is (= "aba-caba" (edn-config/camel->kebab "AbaCaba"))))

(deftest parse-attributes-test
  (is (= {} (edn-config/parse-attributes testing-xml {})))
  (is (= {:type :family}
         (edn-config/parse-attributes
          testing-xml
          {:type keyword})))
  (is (= {:sub-type :brothers}
         (edn-config/parse-attributes
          testing-xml
          {:sub-type keyword})))
  (is (= {}
         (edn-config/parse-attributes
          testing-xml
          {:garbage keyword}))))

(deftest ->int-test
  (is (= 15 (edn-config/->int "15"))))

(deftest ->keyword-test
  (is (= :dada (edn-config/->keyword "dada")))
  (is (= :da-da (edn-config/->keyword "da_da")))
  (is (= :dadada (edn-config/->keyword "DADADA"))))

(deftest tag->holiday-test
  (is (= :islamic-holiday
         (edn-config/tag->holiday :tns:IslamicHoliday)))

  (is (= :fixed-weekday
         (edn-config/tag->holiday :tns:FixedWeekday)))

  (is (= :hindu-holiday
         (edn-config/tag->holiday :tns:HinduHoliday)))

  (is (= :hebrew-holiday
         (edn-config/tag->holiday :tns:HebrewHoliday)))

  (is (= :fixed-weekday-between-fixed
         (edn-config/tag->holiday :tns:FixedWeekdayBetweenFixed)))

  (is (= :fixed-weekday-relative-to-fixed
         (edn-config/tag->holiday :tns:FixedWeekdayRelativeToFixed)))

  (is (= :relative-to-weekday-in-month
         (edn-config/tag->holiday :tns:RelativeToWeekdayInMonth)))

  (is (= :relative-to-fixed
         (edn-config/tag->holiday :tns:RelativeToFixed)))

  (is (= :relative-to-easter-sunday
         (edn-config/tag->holiday :tns:RelativeToEasterSunday)))

  (is (= :ethiopian-orthodox-holiday
         (edn-config/tag->holiday :tns:EthiopianOrthodoxHoliday)))

  (is (= :christian-holiday
         (edn-config/tag->holiday :tns:ChristianHoliday)))

  (is (= :fixed
         (edn-config/tag->holiday :tns:Fixed))))

(defn xml->map
  ""
  [s]
  (xml/parse (java.io.ByteArrayInputStream. (.getBytes s))))


(deftest parse-moving-conditions-test
  (is (= {:moving-conditions [{:substitute :saturday, :with :next, :weekday :monday}]}
         (-> "<container>
                <tns:MovingCondition substitute='SATURDAY'
                                     with='NEXT'
                                     weekday='MONDAY'/>
              </container>"
             xml->map
             edn-config/parse-moving-conditions))))

(deftest parse-holiday-test
  (testing "For fixed"
    (is (= {:holiday :fixed, :month :february, :day 28}
           (-> "<tns:Fixed month='FEBRUARY' day='28' />"
               xml->map
               edn-config/parse-holiday)))

    (is (= {:holiday           :fixed
            :month             :february
            :day               28
            :moving-conditions [{:substitute :saturday,
                                 :with       :next,
                                 :weekday    :monday}]}
           (-> "<tns:Fixed month='FEBRUARY' day='28'>
                  <tns:MovingCondition substitute='SATURDAY'
                                       with='NEXT'
                                       weekday='MONDAY'/>
                </tns:Fixed>"
               xml->map
               edn-config/parse-holiday))))

  (testing "For relative to fixed"
    (is (= {:holiday         :relative-to-fixed
            :description-key :victoria-day
            :weekday         :monday
            :when            :before
            :date            {:month :may, :day 24}}
           (-> "<tns:RelativeToFixed descriptionPropertiesKey='VICTORIA_DAY'>
                  <tns:Weekday>MONDAY</tns:Weekday>
                  <tns:When>BEFORE</tns:When>
                  <tns:Date month='MAY' day='24'/>
  		</tns:RelativeToFixed>"
               xml->map
               edn-config/parse-holiday))))

  (is (= {:holiday :relative-to-fixed
          :days    5
          :when    :after
          :date    {:month :november, :day 23}}
         (-> "<tns:RelativeToFixed >
                <tns:Days>5</tns:Days>
                <tns:When>AFTER</tns:When>
                <tns:Date month='NOVEMBER' day='23'/>
              </tns:RelativeToFixed>"
             xml->map
             edn-config/parse-holiday)))

  (testing "For relative to weekday in month"
    (is (= {:holiday         :relative-to-weekday-in-month
            :weekday         :tuesday
            :when            :after
            :description-key :election
            :fixed-weekday   {:which   :first
                              :weekday :monday
                              :month   :may}}
           (-> "<tns:RelativeToWeekdayInMonth weekday='TUESDAY'
                                              when='AFTER'
                                              descriptionPropertiesKey='ELECTION'>
			<tns:FixedWeekday which='FIRST' weekday='MONDAY' month='MAY'/>
		</tns:RelativeToWeekdayInMonth>"
               xml->map
               edn-config/parse-holiday))))

  (testing "For fixed weekday in month"
    (is (= {:holiday         :fixed-weekday
            :which           :last
            :weekday         :monday
            :month           :may
            :valid-from      1968
            :description-key :memorial}
           (-> "<tns:FixedWeekday
                  which='LAST'
                  weekday='MONDAY'
                  month='MAY'
                  validFrom='1968'
                  descriptionPropertiesKey='MEMORIAL'/>"
               xml->map
               edn-config/parse-holiday))))

  (testing "For christian holiday"
    (is (= {:holiday :christian-holiday, :type :clean-monday, :chronology :julian}
           (-> "<tns:ChristianHoliday type='CLEAN_MONDAY' chronology='JULIAN' />"
               xml->map
               edn-config/parse-holiday)))

    (is (= {:holiday           :christian-holiday
            :type              :clean-monday
            :chronology        :julian
            :moving-conditions [{:substitute :saturday,
                                 :with       :next,
                                 :weekday    :monday}]}
           (-> "<tns:ChristianHoliday type='CLEAN_MONDAY' chronology='JULIAN'>
                  <tns:MovingCondition substitute='SATURDAY'
                                       with='NEXT'
                                       weekday='MONDAY'/>
                </tns:ChristianHoliday>"
               xml->map
               edn-config/parse-holiday))))

  (testing "For islamic holiday"
    (is (= {:holiday :islamic-holiday
            :type    :id-ul-adha}
           (-> "<tns:IslamicHoliday type='ID_UL_ADHA'/>"
               xml->map
               edn-config/parse-holiday))))

  (testing "For fixed weekday between fixed"
    (is (= {:holiday         :fixed-weekday-between-fixed
            :weekday         :saturday
            :description-key :all-saints
            :from            {:month :october, :day 31}
            :to              {:month :november, :day 6}}
           (-> "<tns:FixedWeekdayBetweenFixed weekday='SATURDAY'
                                              descriptionPropertiesKey='ALL_SAINTS'>
                  <tns:from month='OCTOBER' day='31' />
                  <tns:to month='NOVEMBER' day='6' />
                </tns:FixedWeekdayBetweenFixed>"
               xml->map
               edn-config/parse-holiday))))

  (testing "For fixed weekday relative to fixed"
    (is (= {:holiday         :fixed-weekday-relative-to-fixed
            :which           :first
            :weekday         :thursday
            :when            :after
            :description-key :first-day-summer
            :date            {:month :april, :day 18}}
           (-> "<tns:FixedWeekdayRelativeToFixed which='FIRST'
                                                 weekday='THURSDAY'
                                                 when='AFTER'
                                                 descriptionPropertiesKey='FIRST_DAY_SUMMER'>
                  <tns:day month='APRIL' day='18'/>
                </tns:FixedWeekdayRelativeToFixed>"
               xml->map
               edn-config/parse-holiday))))

  (testing "For hindu holiday"
    (is (= {:holiday :hindu-holiday
            :type    :holi}
           (-> "<tns:HinduHoliday type='HOLI' />"
               xml->map
               edn-config/parse-holiday))))

  (testing "For hebrew holiday"
    (is (= {:holiday :hebrew-holiday
            :type    :yom-kippur}
           (-> "<tns:HebrewHoliday type='YOM_KIPPUR' />"
               xml->map
               edn-config/parse-holiday))))

  (testing "For ethiopian orthodox holiday"
    (is (= {:holiday :ethiopian-orthodox-holiday
            :type    :timkat}
           (-> "<tns:EthiopianOrthodoxHoliday type='TIMKAT'/>"
               xml->map
               edn-config/parse-holiday))))

  (testing "For relative to easter sunday"
    (is (= {:holiday    :relative-to-easter-sunday
            :chronology :julian
            :days       12}
           (-> "<tns:RelativeToEasterSunday>
                  <tns:chronology>JULIAN</tns:chronology>
                  <tns:days>12</tns:days>
                </tns:RelativeToEasterSunday>"
               xml->map
               edn-config/parse-holiday)))))

(deftest sort-map-test
  (is (= "{:month :may, :day 1}"
         (-> {:day 1, :month :may}
             edn-config/sort-map
             pr-str)))
  (is (thrown-with-msg? Exception #"Unhandled keys :foo"
                        (edn-config/sort-map {:foo 1}))))

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
