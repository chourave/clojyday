(ns ferje.config.edn-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ferje.config.edn :as edn-config]
   [ferje.spec-test-utils :refer [instrument-fixture]]))

;; Fixtures

(use-fixtures :once instrument-fixture)


;;

(deftest moving-conditions-test
  (is (s/valid? `edn-config/moving-conditions
                 [:substitute :monday :with :next :friday]))
  (is (s/valid? `edn-config/moving-conditions
                 [:substitute :tuesday :with :previous :saturday]))
  (is (s/valid? `edn-config/moving-conditions
                 [:substitute :monday :next :friday]))
  (is (s/valid? `edn-config/moving-conditions
                [:substitute :saturday :with :next :monday,
                 :substitute :sunday :with :next :monday]))
  (is (s/valid? `edn-config/moving-conditions
                [:substitute :saturday :with :next :monday,
                             :sunday :with :next :monday])))

(defn holiday-type
  [value]
  (->> value (s/conform `edn-config/holiday) edn-config/holiday-type))

(deftest holiday-test
  (testing "fixed"
    (is (= :fixed (holiday-type [:january 1])))
    (is (= :fixed (holiday-type [:march 30 :substitute :monday :with :next :tuesday])))
    (is (= :fixed (holiday-type [:december 15 :valid-from 1931 :valid-to 1950])))
    (is (= :fixed (holiday-type [:april 1 :every :5-years])))
    (is (= :fixed (holiday-type [:june 21 :description-key :summer])))
    (is (= :fixed (holiday-type [:august 8 :inofficial]))))
  (testing "relative to fixed"
    (is (= :relative-to-fixed (holiday-type [3 :before :january 1])))
    (is (= :relative-to-fixed (holiday-type [8 :days :before :march 1])))
    (is (= :relative-to-fixed (holiday-type [:monday :before :june 27]))))
  (testing "fixed weekday between fixed"
    (is (= :fixed-weekday-between-fixed (holiday-type [:monday :between :june 27 :and :july 1])))
    (is (= :fixed-weekday-between-fixed (holiday-type [:monday :between :june 27 :july 1]))))
  (testing "fixed weekday"
    (is (= :fixed-weekday (holiday-type [:first :monday :of :march])))
    (is (= :fixed-weekday (holiday-type [:last :friday :of :june])))
    (is (= :fixed-weekday (holiday-type [:third :wednesday :march]))))
  (testing "relative to weekday in month"
    (is (= :relative-to-weekday-in-month (holiday-type [:monday :before :last :tuesday :of :march])))
    (is (= :relative-to-weekday-in-month (holiday-type [:monday :after :second :friday :march]))))
  (testing "fixed weekday relative to fixed"
    (is (= :fixed-weekday-relative-to-fixed (holiday-type [:first :monday :after :june 28])))
    (is (= :fixed-weekday-relative-to-fixed (holiday-type [:second :tuesday :before :november 11]))))
  (testing "christian holiday"
    (is (= :christian-holiday (holiday-type :easter)))
    (is (= :christian-holiday (holiday-type [:easter])))
    (is (= :christian-holiday (holiday-type [:easter :valid-to 1901])))
    (is (= :christian-holiday (holiday-type [:julian :easter])))
    (is (= :christian-holiday (holiday-type [:julian :easter :substitute :sunday :with :next :monday]))))
  (testing "relative to easter sunday"
    (is (= :relative-to-easter-sunday (holiday-type [3 :days :before :easter])))
    (is (= :relative-to-easter-sunday (holiday-type [3 :days :after :easter])))
    (is (= :relative-to-easter-sunday (holiday-type [5 :after :easter])))
    (is (= :relative-to-easter-sunday (holiday-type [-1 :easter])))
    (is (= :relative-to-easter-sunday (holiday-type [3 :days :before :julian :easter]))))
  (testing "islamic holiday"
    (is (= :islamic-holiday (holiday-type :ramadan)))
    (is (= :islamic-holiday (holiday-type [:newyear :valid-from 2001]))))
  (testing "hindu holiday"
    (is (= :hindu-holiday (holiday-type :holi)))
    (is (= :hindu-holiday (holiday-type [:holi :valid-from 2001]))))
  (testing "hebrew holiday"
    (is (= :hebrew-holiday (holiday-type :sukkot)))
    (is (= :hebrew-holiday (holiday-type [:pesach :valid-from 2001]))))
  (testing "ethiopian orthodox holiday"
    (is (= :ethiopian-orthodox-holiday (holiday-type :timkat)))
    (is (= :ethiopian-orthodox-holiday (holiday-type [:meskel :valid-from 2001])))))

(deftest edn->holiday-test
  (testing "fixed"
    (is (= {:holiday :fixed, :month :january, :day 5}
           (edn-config/edn->holiday [:january 5])))

    (is (= {:holiday           :fixed, :month :january, :day 5
            :moving-conditions [{:substitute :monday :with :next :weekday :monday}]}
           (edn-config/edn->holiday [:january 5
                                     :substitute :monday :with :next :monday])))

    (is (= {:holiday           :fixed, :month :january, :day 5
            :moving-conditions [{:substitute :monday :with :next :weekday :sunday}
                                {:substitute :saturday :with :previous :weekday :friday}]}
           (edn-config/edn->holiday [:january 5
                                     :substitute :monday :with :next :sunday,
                                     :saturday :with :previous :friday])))

    (is (= {:holiday           :fixed, :month :january, :day 5
            :moving-conditions [{:substitute :monday :with :next :weekday :sunday}
                                {:substitute :saturday :with :previous :weekday :friday}]}
           (edn-config/edn->holiday [:january 5
                                     :substitute :monday :with :next :sunday,
                                     :substitute :saturday :with :previous :friday]))))

  (testing "relative to fixed"
    (is (= {:holiday :relative-to-fixed
            :when    :before
            :days    3
            :date    {:month :january, :day 1}}
           (edn-config/edn->holiday [3 :before :january 1])))

    (is (= {:holiday :relative-to-fixed
            :when    :after
            :days    8
            :date    {:month :march, :day 1}}
           (edn-config/edn->holiday [8 :days :after :march 1])))

    (is (= {:holiday :relative-to-fixed
            :weekday :monday
            :when    :before
            :date    {:month :june, :day 27}}
           (edn-config/edn->holiday [:monday :before :june 27]))))

  (testing "fixed weekday between fixed"
    (is (= {:holiday :fixed-weekday-between-fixed
            :from    {:month :june, :day 27}
            :to      {:month :july, :day 1}
            :weekday :monday}
           (edn-config/edn->holiday [:monday :between :june 27 :and :july 1])))

    (is (= {:holiday :fixed-weekday-between-fixed
            :from    {:month :june, :day 27}
            :to      {:month :july, :day 1}
            :weekday :monday}
           (edn-config/edn->holiday [:monday :between :june 27 :july 1]))))

  (testing "fixed weekday"
    (is (= {:holiday :fixed-weekday, :month :june, :which :last, :weekday :friday}
           (edn-config/edn->holiday [:last :friday :of :june])))

    (is (= {:holiday :fixed-weekday, :month :march, :which :third, :weekday :wednesday}
           (edn-config/edn->holiday [:third :wednesday :march]))))

  (testing "relative to weekday in month"
    (is (= {:holiday       :relative-to-weekday-in-month
            :weekday       :monday,
            :when          :before,
            :fixed-weekday {:month :march, :which :last, :weekday :tuesday}}
           (edn-config/edn->holiday [:monday :before :last :tuesday :of :march])))

    (is (= {:holiday       :relative-to-weekday-in-month
            :weekday       :monday,
            :when          :after,
            :fixed-weekday {:month :march, :which :second, :weekday :friday}}
           (edn-config/edn->holiday [:monday :after :second :friday :march]))))

  (testing "fixed weekday relative to fixed"
    (is (= {:holiday :fixed-weekday-relative-to-fixed
            :which   :first
            :weekday :monday
            :when    :after
            :date    {:month :june, :day 28}}
           (edn-config/edn->holiday [:first :monday :after :june 28])))

    (is (= {:holiday :fixed-weekday-relative-to-fixed
            :which   :second
            :weekday :tuesday
            :when    :before
            :date    {:month :november, :day 11}}
           (edn-config/edn->holiday [:second :tuesday :before :november 11]))))

  (testing "christian holiday"
    (is (= {:holiday :christian-holiday, :type :easter}
           (edn-config/edn->holiday :easter)))

    (is (= {:holiday :christian-holiday, :type :easter}
           (edn-config/edn->holiday [:easter])))

    (is (= {:holiday :christian-holiday, :type :easter, :chronology :julian}
           (edn-config/edn->holiday [:julian :easter])))

    (is (= {:holiday           :christian-holiday, :type :easter
            :moving-conditions [{:substitute :monday :with :next :weekday :monday}]}
           (edn-config/edn->holiday [:easter :substitute :monday :with :next :monday]))))

  (testing "relative to easter sunday"
    (is (= {:holiday    :relative-to-easter-sunday
            :days       -3
            :chronology :gregorian}
           (edn-config/edn->holiday [3 :days :before :easter])))

    (is (= {:holiday    :relative-to-easter-sunday
            :days       3
            :chronology :gregorian}
           (edn-config/edn->holiday [3 :days :after :easter])))

    (is (= {:holiday    :relative-to-easter-sunday
            :days       5
            :chronology :gregorian}
           (edn-config/edn->holiday [5 :after :easter])))

    (is (= {:holiday    :relative-to-easter-sunday
            :days       -1
            :chronology :gregorian}
           (edn-config/edn->holiday [-1 :easter])))

    (is (= {:holiday    :relative-to-easter-sunday
            :days       -3
            :chronology :julian}
           (edn-config/edn->holiday [3 :days :before :julian :easter]))))

  (testing "islamic holiday"
    (is (= {:holiday :islamic-holiday, :type :ramadan}
           (edn-config/edn->holiday :ramadan))))

  (testing "hindu holiday"
    (is (= {:holiday :hindu-holiday, :type :holi}
           (edn-config/edn->holiday :holi))))

  (testing "hebrew holiday"
    (is (= {:holiday :hebrew-holiday, :type :pesach}
           (edn-config/edn->holiday :pesach))))

  (testing "ethiopian orthodox holiday"
    (is (= {:holiday :ethiopian-orthodox-holiday, :type :timkat}
           (edn-config/edn->holiday :timkat))))

  (testing "common options"
    (is (= {:holiday :hebrew-holiday, :type :hanukkah, :valid-from 1902}
           (edn-config/edn->holiday [:hanukkah :valid-from 1902])))

    (is (= {:holiday :fixed, :day 1, :month :january, :valid-to 1801}
           (edn-config/edn->holiday [:january 1 :valid-to 1801])))

    (is (= {:holiday :christian-holiday, :type :easter, :every :6-years}
           (edn-config/edn->holiday [:easter :every :6-years])))

    (is (= {:holiday :christian-holiday, :type :easter, :chronology :julian, :description-key :fnord}
           (edn-config/edn->holiday [:julian :easter :description-key :fnord])))

    (is (= {:holiday :christian-holiday, :type :easter, :localized-type :unofficial-holiday}
           (edn-config/edn->holiday [:easter :unofficial])))

    (is (= {:holiday :christian-holiday, :type :easter, :localized-type :unofficial-holiday}
           (edn-config/edn->holiday [:easter :inofficial])))

    (is (= {:holiday :christian-holiday, :type :easter, :localized-type :official-holiday}
           (edn-config/edn->holiday [:easter :official])))))


(deftest holiday->edn-test
  (testing "fixed"
    (is (= [:january 5]
           (edn-config/holiday->edn {:holiday :fixed, :month :january, :day 5})))

    (is (= [:march 30 :substitute :sunday :with :previous :friday, :monday :with :next :tuesday]
           (edn-config/holiday->edn
            {:holiday           :fixed, :month :march, :day 30
             :moving-conditions [{:substitute :sunday :with :previous :weekday :friday}
                                 {:substitute :monday :with :next :weekday :tuesday}]}))))

  (testing "relative to fixed"
    (is (= [8 :days :after :march 1]
           (edn-config/holiday->edn
            {:holiday :relative-to-fixed
             :when    :after
             :days    8
             :date    {:month :march, :day 1}})))

    (is (= [:monday :before :june 27]
           (edn-config/holiday->edn
            {:holiday :relative-to-fixed
             :weekday :monday
             :when    :before
             :date    {:month :june, :day 27}}))))

  (testing "fixed weekday between fixed"
    (is (= [:monday :between :june 27 :and :july 1]
           (edn-config/holiday->edn
            {:holiday :fixed-weekday-between-fixed
             :from    {:month :june, :day 27}
             :to      {:month :july, :day 1}
             :weekday :monday}))))

  (testing "fixed weekday"
    (is (= [:last :friday :of :june]
           (edn-config/holiday->edn
            {:holiday :fixed-weekday, :month :june, :which :last, :weekday :friday}))))

  (testing "relative to weekday in month"
    (is (= [:monday :before :last :tuesday :of :march]
           (edn-config/holiday->edn
            {:holiday       :relative-to-weekday-in-month
             :weekday       :monday,
             :when          :before,
             :fixed-weekday {:month :march, :which :last, :weekday :tuesday}}))))

  (testing "fixed weekday relative to fixed"
    (is (= [:first :monday :after :june 28]
           (edn-config/holiday->edn
            {:holiday :fixed-weekday-relative-to-fixed
             :which   :first
             :weekday :monday
             :when    :after
             :date    {:month :june, :day 28}}))))

  (testing "christian holiday"
    (is (= :easter
           (edn-config/holiday->edn {:holiday :christian-holiday, :type :easter})))

    (is (= [:julian :easter]
           (edn-config/holiday->edn {:holiday :christian-holiday, :type :easter, :chronology :julian})))

    (is (= [:easter :substitute :sunday :with :previous :friday]
           (edn-config/holiday->edn
            {:holiday           :christian-holiday, :type :easter
             :moving-conditions [{:substitute :sunday :with :previous :weekday :friday}]}))))

  (testing "relative to easter sunday"
    (is (= [3 :days :before :gregorian :easter]
           (edn-config/holiday->edn
            {:holiday    :relative-to-easter-sunday
             :days       -3
             :chronology :gregorian})))

    (is (= [3 :days :after :julian :easter]
           (edn-config/holiday->edn
            {:holiday    :relative-to-easter-sunday
             :days       3
             :chronology :julian}))))

  (testing "islamic holiday"
    (is (= :ramadan
           (edn-config/holiday->edn {:holiday :islamic-holiday, :type :ramadan}))))

  (testing "hindu holiday"
    (is (= :holi
           (edn-config/holiday->edn {:holiday :hindu-holiday, :type :holi}))))

  (testing "hebrew holiday"
    (is (= :hanukkah
           (edn-config/holiday->edn {:holiday :hebrew-holiday, :type :hanukkah}))))

  (testing "ethiopian orthodox holiday"
    (is (= :meskel
           (edn-config/holiday->edn {:holiday :ethiopian-orthodox-holiday, :type :meskel}))))

  (testing "common options"
    (is (= [:hanukkah :valid-from 1902]
           (edn-config/holiday->edn {:holiday :hebrew-holiday, :type :hanukkah, :valid-from 1902})))

    (is (= [:january 1 :valid-to 1801]
           (edn-config/holiday->edn {:holiday :fixed, :day 1, :month :january, :valid-to 1801})))

    (is (= [:easter :every :6-years]
           (edn-config/holiday->edn {:holiday :christian-holiday, :type :easter, :every :6-years})))

    (is (= [:julian :easter :description-key :fnord]
           (edn-config/holiday->edn {:holiday :christian-holiday, :type :easter, :chronology :julian, :description-key :fnord})))

    (is (= [:easter :unofficial]
           (edn-config/holiday->edn {:holiday :christian-holiday, :type :easter, :localized-type :unofficial-holiday})))))

; todo : options for parse
