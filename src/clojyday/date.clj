;; Copyright and license information at end of file

(ns clojyday.date

  (:require
   [clojure.spec.alpha :as s]
   [clojyday.util :refer [$]]
   [java-time :as time])

  (:import
   (java.time LocalDate)))


;; Basic type predicates

(defn local-date?
  "Is the argument a local date?"
  [x]
  (instance? LocalDate x))

(s/fdef local-date?, :args any?, :ret boolean?)


(defn local-dateable?
  "Can the argument be converted to a LocalDate?"
  [x]
  (try
    (time/local-date x)
    true
    (catch Exception e
      false)))

(s/fdef local-dateable?, :args any?, :ret boolean?)


;; Basic field types

(s/def ::year int?)

(s/def ::from local-date?)

(s/def ::to local-date?)


;; Other basic types

(s/def month int?)

(s/def year-month
  (s/map-of ::year `month :count 1))

(s/def date-stake
  (s/or :year     ::year
        :month    `year-month
        :dateable local-dateable?))

(s/def date-or-interval
  (s/or :date     `date-stake
        :interval (s/cat :from `date-stake :to `date-stake)))


;; Other predicates

(defn date-matches-year-month?
  "Are the year and month of the `date` the same as the ones in the {year month}?"
  [date year-month]
  (= (-> year-month seq first) (time/as date :year :month-of-year)))

(s/fdef date-matches-year-month?
  :args (s/cat :date local-date? :year-month `year-month))


(defn date-matches-stake?
  "Does the `date` match the stake with apropriate precision (same year if the
  stake is a year, same year and month if it is a {year month}, full match if
  it is a full date)"
  [date conformed-stake]
  (let [[type value] conformed-stake]
    (case type
      :year     (= value (time/as date :year))
      :month    (date-matches-year-month? date value)
      :dateable (= (time/local-date value) date))))

(s/fdef date-matches-stake?
  :args (s/cat :date local-date?
               :conformed-stake (s/or :year     (s/cat :key #{:year}     :val ::year)
                                      :month    (s/cat :key #{:month}    :val `year-month)
                                      :dateable (s/cat :key #{:dateable} :val local-dateable?)))
  :ret  boolean?)


;; Parsing a date

(defn month?
  "Is `date-or-interval` of the form {year month}?"
  [date-or-interval]
  (and (map? date-or-interval) (= 1 (count date-or-interval))))

(s/fdef month?
  :args (s/cat :date-or-interval `date-or-interval)
  :ret  boolean?
  :fn   #(let [[date-vs-interval value] (get-in % [:args :date-or-interval])]
           (= (:ret %)
              (and (= :date date-vs-interval)
                   (= :month (first value))))))


(defn parse-month
  "If `date-or-interval` is a {year month}, return it as a local-date"
  [date-or-interval]
  (when (month? date-or-interval)
    (let [[[year month]] (seq date-or-interval)]
      (time/local-date year month))))

(s/fdef parse-month
  :args (s/cat :date-or-interval `date-or-interval)
  :ret  (s/nilable local-date?))

(defn parse-boundary
  "Parse a year / {year month} / local-date, adjusting it to the beginning or end of period"
  [date year-adjust month-adjust]
  (condp $ date
    int?        (time/adjust (time/local-date date) year-adjust)
    parse-month :>> #(time/adjust % month-adjust)
    (time/local-date date)))

(s/fdef parse-boundary
  :args (s/cat :date         `date-stake
               :year-adjust  #{:first-day-of-year :last-day-of-year}
               :month-adjust #{:first-day-of-month :last-day-of-month})
  :ret local-date?
  :fn  #(date-matches-stake? (:ret %) (get-in % [:args :date])))

(defn parse-date-or-interval
  "Parse a date-stake or [date-stake-from date-stake-to]"
  [date-or-interval]
  (condp $ date-or-interval
    int?
    {::year date-or-interval}

    parse-month :>>
    (fn [date]
      {::from (time/adjust date :first-day-of-month)
       ::to   (time/adjust date :last-day-of-month)})

    seqable?
    (let [[from to] date-or-interval]
      {::from (parse-boundary from :first-day-of-year :first-day-of-month)
       ::to   (parse-boundary to :last-day-of-year :last-day-of-month)})

    (time/local-date date-or-interval)))

(s/fdef parse-date-or-interval
  :args (s/cat :date-or-interval `date-or-interval)
  :ret  (s/or :year     (s/keys :req [::year])
              :interval (s/keys :req [::from ::to]))
  :fn (fn [{:keys [args ret]}]
        (let [[type value] (:date-or-interval args)]
          (case type

            :date
            (let [[type date] value]
              (case type
                :year (= ret {::year date})

                :month
                (let [{::keys [from to]} ret]
                  (and (date-matches-year-month? from date)
                       (date-matches-year-month? to date)))

                :dateable
                (let [{::keys [from to]} ret]
                  (= date from to))))

            :interval
            (let [{:keys [from to]} value]
              (and (date-matches-stake? (::from ret) from)
                   (date-matches-stake? (::to ret) to)))))))

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
