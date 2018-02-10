;; Copyright and license information at end of file

(ns ferje.core
  "User facing API of ferje"
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [ferje.date :as date]
   [ferje.config.edn :as edn-config]
   [ferje.config.xml]
   [ferje.localized :refer [-localize localized? Localized]]
   [ferje.place :as place]
   [java-time :as time])

  (:import
   (de.jollyday CalendarHierarchy HolidayManager HolidayType)
   (de.jollyday.util ResourceUtil)
   (java.util Locale)))


(try (set! *warn-on-reflection* true) (catch IllegalStateException e))


;; Basic field types

(s/def ::id keyword?)

(s/def ::description (s/nilable string?))

(s/def ::description-key string?)

(s/def calendar (s/keys :req-un [::id ::description ::description-key ::zones]))

(s/def ::zones (s/nilable (s/map-of ::id `calendar)))

(s/def ::date date/local-date?)

(s/def ::official? boolean?)

(s/def holiday (s/keys :req-un [::date ::description ::description-key ::official?]))

;; Localization

(defn localize
  "Returns a version with a :description for the specified locale,
  or the default locale if none was specified"
  ([localized]
   (localize localized (Locale/getDefault)))
  ([localized locale]
   (-localize localized (ResourceUtil.) locale)))

(s/fdef localize
  :args (s/cat :localized localized?, :locale (s/? place/locale?))
  :ret any?
  :fn #(= (-> % :args :localized type)
          (-> % :ret type)))


;; Calendars

(defrecord Calendar [id description description-key zones]
  Localized
  (-localize [h resource-util locale]
    (assoc h

           :description
           (.getCountryDescription
             ^ResourceUtil resource-util
             locale
             description-key)

           :zones
           (when zones
             (into {}
                   (map (fn [[id calendar]]
                          [id (-localize calendar resource-util locale)]))
                   zones)))))


(defn parse-calendar
  "Build a map from Jollyday calendar hierarchy"
  ([calendar]
   (parse-calendar calendar ""))
  ([^CalendarHierarchy calendar key-prefix]
   (let [id              (.getId calendar)
         description-key (str key-prefix id)
         key-prefix      (str description-key ".")]
     (-> {:id
          (keyword id)

          :description-key
          description-key

          :zones
          (when-let [children (seq (.getChildren calendar))]
            (into {}
                  (map (fn [[id calendar]]
                         [(keyword id) (parse-calendar calendar key-prefix)]))
                  children))}
         map->Calendar))))

(s/fdef parse-calendar
  :arg (s/cat :calendar   #(instance? CalendarHierarchy %)
              :key-prefix (s/? string?))
  :ret `calendar)


(defn calendar-names
  "List all the supported holiday calendars"
  []
  (into #{}
        (map (comp keyword string/lower-case))
        (HolidayManager/getSupportedCalendarCodes)))

(s/fdef calendar-names
  :args empty?
  :ret  (s/coll-of place/holiday-calendars :kind set?))


(defn convert-standard-calendars
  "Convert the xml callendars that come with Jollyday to
  edn calendars, and write them to `target-dir`.
  Optionally format the output in a pleasing way (the process
  is much slower though)"
  ([target-dir]
   (convert-standard-calendars false))
  ([target-dir pretty?]
   (let [convert (if pretty?
                   edn-config/pretty-convert
                   edn-config/fast-convert)]
     (doseq [cal (calendar-names)]
       (edn-config/xml->edn target-dir convert cal)))))

(s/fdef convert-standard-calendars
  :args (s/cat :targe-dir string? :pretty? (s/? boolean?)))


(def default-config-format
  "The calendar configuration file format that will be used when none is specified"
  :xml-clj)


(defn calendar-hierarchy
  "Returns the id and (localizable) descriptions of a geographical place and its subdivisions"
  ([place]
   (calendar-hierarchy default-config-format place))
  ([config-format place]
   (let [{::place/keys [zones ^HolidayManager manager]}
         (place/parse-place config-format place)

         calendar (parse-calendar (.getCalendarHierarchy manager))]
     (if (seq zones)
       (get-in calendar (mapcat #(vector :zones (keyword %)) zones))
       calendar))))

(s/fdef calendar-hierarchy
  :args (s/cat :config-format (s/? place/format?)
               :place `place/calendar-and-zones)
  :ret  `calendar)

(defrecord Calendars []
  Localized
  (-localize [cs resource-util locale]
    (->> cs
         (into {} (map #(vector (key %) (-localize (val %) resource-util locale))))
         map->Calendars)))


(defn calendars
  "Returns the ids and (localizable) descriptions of all places that have predefined
  calendar rules, among with their subdivisions"
  []
  (->> (calendar-names)
       (into {}
             (map #(vector % (calendar-hierarchy %))))
       map->Calendars))


(s/fdef calendars
  :args (s/cat)
  :ret (s/map-of keyword? `calendar))


;; Holidays

(defrecord Holiday [date description description-key official?]
  Localized
  (-localize [h resource-util locale]
    (assoc h
      :description
      (.getHolidayDescription ^ResourceUtil resource-util locale description-key))))


(defn parse-holiday
  "Parse a Jollyday holliday object into a clojure localizable record"
  [^de.jollyday.Holiday holiday]
  (-> {:description-key (.getPropertiesKey holiday)
       :date            (.getDate holiday)
       :official?       (= HolidayType/OFFICIAL_HOLIDAY (.getType holiday))}
      map->Holiday))

(s/fdef parse-holiday
  :args (s/cat :holiday #(instance? de.jollyday.Holiday %))
  :ret `holiday)


(defn holidays
  "Returns the holidays in a given place over a given time period"
  ([place date-or-interval]
   (holidays default-config-format place date-or-interval))
  ([config-format place date-or-interval]
   (let [{::place/keys [zones ^HolidayManager manager]}
         (place/parse-place config-format place)

         {::date/keys [year from to]}
         (date/parse-date-or-interval date-or-interval)]
     (into #{}
           (map parse-holiday)
           (if year
             (.getHolidays manager year zones)
             (.getHolidays manager from to zones))))))

(s/fdef holidays
  :args (s/cat :config-format (s/? place/format?)
               :place `place/calendar-and-zones
               :date-or-interval `date/date-or-interval)
  :ret (s/coll-of `holiday :kind set?))


(def holiday-types
  "All valid holiday types (for `holiday?`)"
  {:official-holiday   HolidayType/OFFICIAL_HOLIDAY
   :unofficial-holiday HolidayType/UNOFFICIAL_HOLIDAY
   :any-holiday        nil})


(defn holiday?
  "Is a given date a holiday in a given place?"
  ([place date]
   (holiday? default-config-format place date :any-holiday))

  ([place date type]
   (apply holiday?
          (if (place/format? place)
           [place date type :any-holiday]
           [default-config-format place date type])))

  ([config-format place date type]
   (let [{::place/keys [^"[Ljava.lang.String;" zones
                        ^HolidayManager ^HolidayManager manager]}
         (place/parse-place config-format place)]
     (.isHoliday
       manager
       (time/local-date date)
       ^HolidayType (holiday-types type)
       zones))))

(s/fdef holiday?
  :args (s/cat :config-format (s/? place/format?)
               :place `place/calendar-and-zones
               :date `date/date-or-interval
               :type (s/? #(contains? holiday-types %)))
  :ret boolean?)


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
