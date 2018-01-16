;; Copyright and license information at end of file

(ns clojyday.core-test

  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [clojure.walk :refer [prewalk]]
   [clojyday.core :as clojyday]
   [clojyday.jaxb-utils :refer [jaxb-fixture]]
   [clojyday.spec-test-utils :refer [instrument-fixture]]
   [java-time :as time])

  (:import
   (clojyday.core Calendar)
   (de.jollyday HolidayCalendar)
   (java.util Locale)))


;; Fixtures

(use-fixtures :once
  (join-fixtures [jaxb-fixture instrument-fixture]))


;; Calendars

(defn filter-calendar [calendar]
  (prewalk #(if (s/valid? `clojyday/calendar %)
              (select-keys % [:id :zones :description-key])
              %)
           calendar))

(deftest calendar-hierarchy-test
  (testing "Parse nested hierarchy"
    (is (= {:id :de
            :description-key "de"
            :zones
            {:bb {:id :bb, :description-key "de.bb", :zones nil}
             :st {:id :st, :description-key "de.st", :zones nil}
             :th {:id :th, :description-key "de.th", :zones nil}
             :bw {:id :bw, :description-key "de.bw", :zones nil}
             :by {:id :by
                  :description-key "de.by"
                  :zones
                  {:re {:id :re, :description-key "de.by.re", :zones nil}
                   :in {:id :in, :description-key "de.by.in", :zones nil}
                   :ag {:id :ag, :description-key "de.by.ag", :zones nil}
                   :mu {:id :mu, :description-key "de.by.mu", :zones nil}
                   :wu {:id :wu, :description-key "de.by.wu", :zones nil}}}
             :mv {:id :mv, :description-key "de.mv", :zones nil}
             :nw {:id :nw, :description-key "de.nw", :zones nil}
             :sl {:id :sl, :description-key "de.sl", :zones nil}
             :sn {:id :sn, :description-key "de.sn", :zones nil}
             :he {:id :he, :description-key "de.he", :zones nil}
             :rp {:id :rp, :description-key "de.rp", :zones nil}}}
           (filter-calendar (clojyday/calendar-hierarchy :de)))))

  (testing "Sub-hierarchy"
    (is (= {:id :re, :description-key "de.by.re", :zones nil}
           (filter-calendar (clojyday/calendar-hierarchy [:de :by :re])))))

  (testing "description"
    (is (= "München"
           (-> (clojyday/calendar-hierarchy :de)
               (clojyday/localize Locale/GERMAN)
               (get-in [:zones :by, :zones :mu, :description]))))))


(deftest calendars-test
  (is (= #{:al :ar :at :au :ba :be :bg :bo :br :by :ca :ch :cl :co :cr :cz :de
           :dk :ec :ee :es :et :fi :fr :gb :gr :hr :hu :ie :is :it :jp :kz :li
           :lt :lu :lv :md :me :mk :mt :mx :ng :ni :nl :no :nyse :nz :pa :pe
           :pl :pt :py :ro :rs :ru :se :si :sk :target :ua :us :uy :ve :za}
         (clojyday/calendars))))


;; Holidays

(deftest parse-holiday-test
  (is ()))

(defn filter-holiday
  "Convert a seqable of Holidays into a set of maps, keeping only
  the :date and :official fields"
  [holidays]
  (into #{}
        (map #(select-keys % [:date :description-key :official?]))
        holidays))

(deftest holidays-test
  (testing "For a whole year"
    (is (= #{{:date (time/local-date 2003 1 1), :official? true, :description-key "NEW_YEAR"}
             {:date (time/local-date 2003 4 20), :official? true, :description-key "christian.EASTER"}
             {:date (time/local-date 2003 4 21), :official? true, :description-key "christian.EASTER_MONDAY"}
             {:date (time/local-date 2003 5 1), :official? true, :description-key "LABOUR_DAY"}
             {:date (time/local-date 2003 5 8), :official? true, :description-key "VICTORY_DAY"}
             {:date (time/local-date 2003 5 29), :official? true, :description-key "christian.ASCENSION_DAY"}
             {:date (time/local-date 2003 6 9), :official? true, :description-key "christian.WHIT_MONDAY"}
             {:date (time/local-date 2003 7 14), :official? true, :description-key "NATIONAL_DAY"}
             {:date (time/local-date 2003 8 15), :official? true, :description-key "ASSUMPTION_MARY"}
             {:date (time/local-date 2003 11 1), :official? true, :description-key "ALL_SAINTS"}
             {:date (time/local-date 2003 11 11), :official? true, :description-key "REMEMBRANCE"}
             {:date (time/local-date 2003 12 25), :official? true, :description-key "CHRISTMAS"}}
           (filter-holiday (clojyday/holidays :fr 2003)))))

  (testing "For a month"
    (is (= #{{:date (time/local-date 2003 8 15), :official? true, :description-key "ASSUMPTION_MARY"}}
           (filter-holiday (clojyday/holidays :fr {2003 8})))))

  (testing "For a custom range"
    (is (= #{{:date (time/local-date 2003 7 14), :official? true, :description-key "NATIONAL_DAY"}
             {:date (time/local-date 2003 8 15), :official? true, :description-key "ASSUMPTION_MARY"}}
           (filter-holiday (clojyday/holidays :fr [{2003 7} {2003 8}])))))

  (testing "description"
    (is (= "Assomption"
           (-> (clojyday/holidays :fr {2003 8})
               first
               (clojyday/localize Locale/FRENCH)
               :description)))))

(deftest holiday?-test
  (testing "The 14th of July is a national holiday in France"
    (is (clojyday/holiday? :fr (time/local-date 2017 7 14))))
  (testing "The 26th of December is a holiday in Alsace"
    (is (not (clojyday/holiday? :fr (time/local-date 2017 12 26))))
    (is (clojyday/holiday? [:fr :br] (time/local-date 2017 12 26)))))


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