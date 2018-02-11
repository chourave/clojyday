;; Copyright and license information at end of file

(ns ferje.config.xml-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.xml :as xml]
   [ferje.config.xml :as xml-config]
   [ferje.place :as place]
   [ferje.spec-test-utils :refer [instrument-fixture]]))


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


(deftest strip-tag-namespace-test
  (is (= "tag"
         (xml-config/strip-tag-namespace :ns:tag)))
  (is (= "tag"
         (xml-config/strip-tag-namespace :tag))))


(deftest strip-namespaces-test
  (is (=
       {:tag   :Gang
        :attrs {:name    "the Daltons"
                :type    "family"
                :subType "brothers"}
        :content
        [{:tag     :GangMember
          :attrs   {:name "Joe" :family "Dalton"}
          :content []}
         {:tag     :GangMember
          :attrs   {:name "Jack" :family "Dalton"}
          :content []}
         {:tag     :Pet
          :attrs   {:name "Rantanplan"}
          :content []}]}
       (xml-config/strip-namespaces testing-xml)))
  (testing "Textual nodes are left alone"
    (is (= {:tag :whatever
            :attrs {}
            :content ["blah"]}
           (xml-config/strip-namespaces
            {:tag :whatever
             :attrs {}
             :content ["blah"]})))))


;;

(deftest format?-test
  (is (place/format? :xml-clj)))


(deftest attribute-test
  (is (= "the Daltons"
         (xml-config/attribute testing-xml :name))))

(deftest elements-test
  (is (= [{:tag     :tns:GangMember
           :attrs   {:name "Joe" :family "Dalton"}
           :content []}
          {:tag     :tns:GangMember
           :attrs   {:name "Jack" :family "Dalton"}
           :content []}]
         (xml-config/elements testing-xml :tns:GangMember)))
  (is (not (xml-config/elements testing-xml :inexistent))))

(deftest element-test
  (is (= {:tag     :tns:Pet
          :attrs   {:name "Rantanplan"}
          :content []}
         (xml-config/element testing-xml :tns:Pet))))

(deftest parse-attributes-test
  (testing "Return an empty map, not nil"
    (is (= {} (xml-config/parse-attributes testing-xml {}))))
  (testing "Find an attribute by its name, feed its value to the parsing function"
    (is (= {:type :family}
           (xml-config/parse-attributes
            testing-xml
            {:type keyword})))
    (is (= {:sub-type :brothers}
           (xml-config/parse-attributes
            testing-xml
            {:sub-type keyword}))))
  (testing "If we don’t have a tag, don’t put a key-value for it into the result"
    (is (= {}
           (xml-config/parse-attributes
            testing-xml
            {:garbage keyword})))))

(deftest ->int-test
  (is (= 15 (xml-config/->int "15"))))

(deftest ->keyword-test
  (is (= :dada (xml-config/->keyword "dada")))
  (is (= :da-da (xml-config/->keyword "da_da")))
  (is (= :dadada (xml-config/->keyword "DADADA"))))

(deftest tag->holiday-test
  (is (= :islamic-holiday
         (xml-config/tag->holiday :IslamicHoliday)))

  (is (= :fixed-weekday
         (xml-config/tag->holiday :FixedWeekday)))

  (is (= :hindu-holiday
         (xml-config/tag->holiday :HinduHoliday)))

  (is (= :hebrew-holiday
         (xml-config/tag->holiday :HebrewHoliday)))

  (is (= :fixed-weekday-between-fixed
         (xml-config/tag->holiday :FixedWeekdayBetweenFixed)))

  (is (= :fixed-weekday-relative-to-fixed
         (xml-config/tag->holiday :FixedWeekdayRelativeToFixed)))

  (is (= :relative-to-weekday-in-month
         (xml-config/tag->holiday :RelativeToWeekdayInMonth)))

  (is (= :relative-to-fixed
         (xml-config/tag->holiday :RelativeToFixed)))

  (is (= :relative-to-easter-sunday
         (xml-config/tag->holiday :RelativeToEasterSunday)))

  (is (= :ethiopian-orthodox-holiday
         (xml-config/tag->holiday :EthiopianOrthodoxHoliday)))

  (is (= :christian-holiday
         (xml-config/tag->holiday :ChristianHoliday)))

  (is (= :fixed
         (xml-config/tag->holiday :Fixed))))

(defn xml->map
  ""
  [s]
  (xml/parse (java.io.ByteArrayInputStream. (.getBytes s))))


(deftest parse-moving-conditions-test
  (is (= {:moving-conditions [{:substitute :saturday, :with :next, :weekday :monday}]}
         (-> "<container>
                <MovingCondition substitute='SATURDAY'
                                     with='NEXT'
                                     weekday='MONDAY'/>
              </container>"
             xml->map
             xml-config/parse-moving-conditions))))

(deftest parse-holiday-test
  (testing "For fixed"
    (is (= {:holiday :fixed, :month :february, :day 28}
           (-> "<Fixed month='FEBRUARY' day='28' />"
               xml->map
               xml-config/parse-holiday)))

    (is (= {:holiday           :fixed
            :month             :february
            :day               28
            :moving-conditions [{:substitute :saturday,
                                 :with       :next,
                                 :weekday    :monday}]}
           (-> "<Fixed month='FEBRUARY' day='28'>
                  <MovingCondition substitute='SATURDAY'
                                       with='NEXT'
                                       weekday='MONDAY'/>
                </Fixed>"
               xml->map
               xml-config/parse-holiday))))

  (testing "For relative to fixed"
    (is (= {:holiday         :relative-to-fixed
            :description-key :victoria-day
            :weekday         :monday
            :when            :before
            :date            {:month :may, :day 24}}
           (-> "<RelativeToFixed descriptionPropertiesKey='VICTORIA_DAY'>
                  <Weekday>MONDAY</Weekday>
                  <When>BEFORE</When>
                  <Date month='MAY' day='24'/>
  		</RelativeToFixed>"
               xml->map
               xml-config/parse-holiday)))

    (is (= {:holiday :relative-to-fixed
            :days    5
            :when    :after
            :date    {:month :november, :day 23}}
           (-> "<RelativeToFixed >
                <Days>5</Days>
                <When>AFTER</When>
                <Date month='NOVEMBER' day='23'/>
              </RelativeToFixed>"
               xml->map
               xml-config/parse-holiday))))

  (testing "For fixed weekday in month"
    (is (= {:holiday         :fixed-weekday
            :which           :last
            :weekday         :monday
            :month           :may
            :valid-from      1968
            :description-key :memorial}
           (-> "<FixedWeekday
                  which='LAST'
                  weekday='MONDAY'
                  month='MAY'
                  validFrom='1968'
                  descriptionPropertiesKey='MEMORIAL'/>"
               xml->map
               xml-config/parse-holiday))))

  (testing "For relative to weekday in month"
    (is (= {:holiday         :relative-to-weekday-in-month
            :weekday         :tuesday
            :when            :after
            :description-key :election
            :fixed-weekday   {:which   :first
                              :weekday :monday
                              :month   :may}}
           (-> "<RelativeToWeekdayInMonth weekday='TUESDAY'
                                              when='AFTER'
                                              descriptionPropertiesKey='ELECTION'>
			<FixedWeekday which='FIRST' weekday='MONDAY' month='MAY'/>
		</RelativeToWeekdayInMonth>"
               xml->map
               xml-config/parse-holiday))))

  (testing "For christian holiday"
    (is (= {:holiday :christian-holiday, :type :clean-monday, :chronology :julian}
           (-> "<ChristianHoliday type='CLEAN_MONDAY' chronology='JULIAN' />"
               xml->map
               xml-config/parse-holiday)))

    (is (= {:holiday           :christian-holiday
            :type              :clean-monday
            :chronology        :julian
            :moving-conditions [{:substitute :saturday,
                                 :with       :next,
                                 :weekday    :monday}]}
           (-> "<ChristianHoliday type='CLEAN_MONDAY' chronology='JULIAN'>
                  <MovingCondition substitute='SATURDAY'
                                       with='NEXT'
                                       weekday='MONDAY'/>
                </ChristianHoliday>"
               xml->map
               xml-config/parse-holiday))))

  (testing "For islamic holiday"
    (is (= {:holiday :islamic-holiday
            :type    :id-ul-adha}
           (-> "<IslamicHoliday type='ID_UL_ADHA'/>"
               xml->map
               xml-config/parse-holiday))))

  (testing "For fixed weekday between fixed"
    (is (= {:holiday         :fixed-weekday-between-fixed
            :weekday         :saturday
            :description-key :all-saints
            :from            {:month :october, :day 31}
            :to              {:month :november, :day 6}}
           (-> "<FixedWeekdayBetweenFixed weekday='SATURDAY'
                                              descriptionPropertiesKey='ALL_SAINTS'>
                  <from month='OCTOBER' day='31' />
                  <to month='NOVEMBER' day='6' />
                </FixedWeekdayBetweenFixed>"
               xml->map
               xml-config/parse-holiday))))

  (testing "For fixed weekday relative to fixed"
    (is (= {:holiday         :fixed-weekday-relative-to-fixed
            :which           :first
            :weekday         :thursday
            :when            :after
            :description-key :first-day-summer
            :date            {:month :april, :day 18}}
           (-> "<FixedWeekdayRelativeToFixed which='FIRST'
                                                 weekday='THURSDAY'
                                                 when='AFTER'
                                                 descriptionPropertiesKey='FIRST_DAY_SUMMER'>
                  <day month='APRIL' day='18'/>
                </FixedWeekdayRelativeToFixed>"
               xml->map
               xml-config/parse-holiday))))

  (testing "For hindu holiday"
    (is (= {:holiday :hindu-holiday
            :type    :holi}
           (-> "<HinduHoliday type='HOLI' />"
               xml->map
               xml-config/parse-holiday))))

  (testing "For hebrew holiday"
    (is (= {:holiday :hebrew-holiday
            :type    :yom-kippur}
           (-> "<HebrewHoliday type='YOM_KIPPUR' />"
               xml->map
               xml-config/parse-holiday))))

  (testing "For ethiopian orthodox holiday"
    (is (= {:holiday :ethiopian-orthodox-holiday
            :type    :timkat}
           (-> "<EthiopianOrthodoxHoliday type='TIMKAT'/>"
               xml->map
               xml-config/parse-holiday))))

  (testing "For relative to easter sunday"
    (is (= {:holiday    :relative-to-easter-sunday
            :chronology :julian
            :days       12}
           (-> "<RelativeToEasterSunday>
                  <chronology>JULIAN</chronology>
                  <days>12</days>
                </RelativeToEasterSunday>"
               xml->map
               xml-config/parse-holiday)))))


(deftest parse-configuration-test
  (testing "With Jollyday namespace prefix"
    (is (= {:description "blah", :hierarchy :bl
            :holidays    [{:holiday :fixed
                           :month   :july
                           :day     14}]}
           (-> "<Configuration description='blah' hierarchy='bl'>
                <Holidays>
                  <Fixed day='14' month='JULY'/>
                </Holidays>
              </Configuration>"
               xml->map
               xml-config/parse-configuration))))
  (testing "With other namespace prefix"
    (is (= {:description "blah", :hierarchy :bl
            :holidays    [{:holiday :fixed
                           :month   :july
                           :day     14}]}
           (-> "<blah:Configuration description='blah' hierarchy='bl'>
                <blah:Holidays>
                  <blah:Fixed day='14' month='JULY'/>
                </blah:Holidays>
              </blah:Configuration>"
               xml->map
               xml-config/parse-configuration))))
  (testing "Without any namespace prefix"
    (is (= {:description "blah", :hierarchy :bl
            :holidays    [{:holiday :fixed
                           :month   :july
                           :day     14}]}
           (-> "<Configuration description='blah' hierarchy='bl'>
                <Holidays>
                  <Fixed day='14' month='JULY'/>
                </Holidays>
              </Configuration>"
               xml->map
               xml-config/parse-configuration)))))

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
