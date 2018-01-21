;; Copyright and license information at end of file

(ns clojyday.core

  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojyday.date :as date]
   [clojyday.localized :refer [-localize localized? Localized]]
   [clojyday.place :as place]
   [java-time :as time])

  (:import
   (de.jollyday CalendarHierarchy HolidayManager HolidayType)
   (de.jollyday.util ResourceUtil)
   (java.util Locale)))


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
  :ret (s/keys :req-un [::description])
  :fn #(= (-> % :args :localized type)
          (-> % :ret type)))


;; Calendars

(defrecord Calendar [id description description-key zones]
  Localized
  (-localize [h resource-util locale]
    (assoc h

           :description
           (.getCountryDescription resource-util locale description-key)

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
  ([calendar key-prefix]
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


(defn calendar-hierarchy
  ""
  ([place]
   (let [{::place/keys [zones manager]} (place/parse-place place)
         calendar (parse-calendar (.getCalendarHierarchy manager))]
     (if (seq zones)
       (get-in calendar (mapcat #(vector :zones (keyword %)) zones))
       calendar))))

(s/fdef calendar-hierarchy
  :args (s/cat :place `place/calendar-and-zones)
  :ret  `calendar)


(defn calendars
  "List all the supported holiday calendars"
  []
  (into #{}
        (map (comp keyword string/lower-case))
        (HolidayManager/getSupportedCalendarCodes)))

(s/fdef calendars
  :args empty?
  :ret  (s/coll-of place/holiday-calendars :kind set?))


;; Holidays

(defrecord Holiday [date description description-key official?]
  Localized
  (-localize [h resource-util locale]
    (assoc h :description (.getHolidayDescription resource-util locale description-key))))


(defn parse-holiday
  ""
  [holiday]
  (-> {:description-key (.getPropertiesKey holiday)
       :date            (.getDate holiday)
       :official?       (= HolidayType/OFFICIAL_HOLIDAY (.getType holiday))}
      map->Holiday))

(s/fdef parse-holiday
  :args (s/cat :holiday #(instance? de.jollyday.Holiday %))
  :ret `holiday)


(defn holidays
  ""
  [place date-or-interval]
  (let [{::place/keys [zones manager]} (place/parse-place place)
        {::date/keys [year from to]}   (date/parse-date-or-interval date-or-interval)]
    (into #{}
          (map parse-holiday)
          (if year
            (.getHolidays manager year zones)
            (.getHolidays manager from to zones)))))


(def holiday-types
  ""
  {:official-holiday   HolidayType/OFFICIAL_HOLIDAY
   :unofficial-holiday HolidayType/UNOFFICIAL_HOLIDAY
   :any-holiday        nil})


(defn holiday?
  ""
  ([place date]
   (holiday? place date :any-holiday))

  ([place date type]
   (let [{::place/keys [zones manager]} (place/parse-place place)]
     (.isHoliday manager (time/local-date date) (holiday-types type) zones))))


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
