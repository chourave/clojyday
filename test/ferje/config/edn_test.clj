(ns ferje.config.edn-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ferje.config.edn :as edn-config]
   [clojure.edn :as edn]))

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

(defn type
  [conformed-holiday]
  (cond
    (= ::s/invalid conformed-holiday)
    conformed-holiday

    (= :composite (key conformed-holiday))
    (-> conformed-holiday val :definition key)

    :else
    (key conformed-holiday)))

(defn holiday-type
  [value]
  (some->> value (s/conform `edn-config/holiday) type))

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
    (is (= :christian-holiday (holiday-type [:easter :julian])))
    (is (= :christian-holiday (holiday-type [:easter :julian :substitute :sunday :with :next :monday]))))
  (testing "relative to easter sunday"
    (is (= :relative-to-easter-sunday (holiday-type [3 :days :before :easter])))
    (is (= :relative-to-easter-sunday (holiday-type [3 :days :after :easter])))
    (is (= :relative-to-easter-sunday (holiday-type [5 :after :easter])))
    (is (= :relative-to-easter-sunday (holiday-type [-1 :easter]))))
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

