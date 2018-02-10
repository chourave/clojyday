;; Copyright and license information at end of file

(ns ferje.config.core
  "Represent Jollyday configuration beans as clojure maps"
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [ferje.util :as util])

  (:import
   (de.jollyday.config
    ChristianHoliday ChristianHolidayType ChronologyType Configuration
    EthiopianOrthodoxHoliday EthiopianOrthodoxHolidayType Fixed
    FixedWeekdayBetweenFixed FixedWeekdayInMonth FixedWeekdayRelativeToFixed
    HebrewHoliday HinduHoliday HinduHolidayType Holiday HolidayType Holidays
    IslamicHoliday IslamicHolidayType Month MovingCondition
    RelativeToEasterSunday RelativeToFixed RelativeToWeekdayInMonth Weekday
    When Which With)))


(s/def ::day (s/int-in 1 32))

(s/def ::month #{:january :february :march :april :may :june
                 :july :august :september :october :november :december})

(s/def ::weekday #{:monday :tuesday :wednesday :thursday :friday :saturday :sunday})

(s/def ::which #{:first :second :third :fourth :last})

(s/def ::when #{:before :after})

(s/def ::valid-from int?)

(s/def ::valid-to int?)

(s/def ::every #{:every-year :2-years :4-years :5-years :6-years :odd-years :even-years})

(s/def ::description-key keyword?)

(s/def ::localized-type #{:official-holiday :unofficial-holiday})

(s/def ::substitute ::weekday)

(s/def ::moving-conditions
  (s/coll-of
   (s/keys :req-un [::substitute ::with ::weekday])))

(s/def ::date
  (s/keys :req-un [::month ::day]
          :opt-un [::moving-conditions]))

(s/def ::days int?)

(s/def ::description string?)

(s/def ::hierarchy keyword?)

(s/def ::holidays (s/coll-of `holiday))

(s/def ::sub-configurations (s/coll-of ::configuration))

(s/def ::configuration
  (s/keys :req-un [::description ::hierarchy ::holidays]
          :opt-un [::sub-configurations]))

(defn holiday?
  "Is x a Jollyday Holiday configuration object?"
  [x]
  (instance? Holiday x))

(s/fdef holiday?
  :args (s/cat :x any?)
  :ret boolean?)


(defn java-collection?
  "Is x a Java collection?"
  [x]
  (instance? java.util.Collection x))

(s/fdef java-collection?
  :args (s/cat :x any?)
  :ret boolean?)


(defn named?
  "Is x a valid argument to the `name` function?"
  [x]
  (instance? clojure.lang.Named x))

(s/fdef named?
  :args (s/cat :x any?)
  :ret boolean?)


(s/def ::calendar-name named?)


(defn ->const-name
  "Parse a :clojure-keyword to a JAVA_CONSTANT_NAME (as a strig)"
  [x]
  (-> x name string/upper-case (string/replace #"-" "_")))

(s/fdef ->const-name
  :args (s/cat :x named?)
  :ret (s/and string? util/uppercase?))


;;

(defmacro ->enum
  "Parse a keyword to a value in a Java Enum"
  [value enum]
  `(-> ~value ->const-name (~(symbol (str enum) "valueOf"))))

(s/fdef ->enum
  :args (s/cat :value any? :enum simple-symbol?)
  :ret any?)


;;

(s/def ::holiday #{:islamic-holiday
                   :fixed-weekday
                   :hindu-holiday
                   :hebrew-holiday
                   :fixed-weekday-between-fixed
                   :fixed-weekday-relative-to-fixed
                   :relative-to-weekday-in-month
                   :relative-to-fixed
                   :relative-to-easter-sunday
                   :ethiopian-orthodox-holiday
                   :christian-holiday
                   :fixed})


(s/def holiday-common
  (s/keys :opt-un [::valid-from ::valid-to ::every ::description-key ::localized-type]))


(s/def holiday-tag-common
  (s/merge `holiday-common
           (s/keys :req-un [::holiday])))


(defn add-moving-conditions!
  "Add the moving conditions from a map to a Jollyday Holiday object"
  [bean config]
  (doto bean
    (-> .getMovingCondition
        (.addAll (map #(doto (MovingCondition.)
                         (.setSubstitute (-> % :substitute (->enum Weekday)))
                         (.setWith (-> % :with (->enum With)))
                         (.setWeekday (-> % :weekday (->enum Weekday))))
                      (:moving-conditions config))))))

(s/fdef add-moving-conditions!
  :args (s/cat :bean holiday? :config map?)
  :ret holiday?
  :fn #(identical? (-> % :ret) (-> % :args :bean)))


(defmulti holiday-spec
  "Returns the spec for a holiday definition"
  :holiday)


(s/def holiday (s/multi-spec holiday-spec :holiday))


(defn set-common-holiday-attributes!
  "Set a Jollyday `holiday`object with all those attributes from `config`
  that are shared among all holiday types."
  [holiday config]
  (doto holiday
    (.setValidFrom (some-> config :valid-from int))
    (.setValidTo (some-> config :valid-to int))
    (.setEvery (some-> config :every ->const-name))
    (.setLocalizedType (some-> config :localized-type (->enum HolidayType)))
    (.setDescriptionPropertiesKey (some-> config :description-key ->const-name))))

(s/fdef set-common-holiday-attributes!
  :args (s/cat :bean holiday? :config `holiday-common)
  :ret holiday?
  :fn #(identical? (-> % :ret) (-> % :args :bean)))


;;

(defmulti ->Holiday
  "Create a Jollyday holiday bean from an edn holiday configuration"
  :holiday)


(defn add-holidays!
  "Add holidays of a given `type` from `all-holidays` collection
  to the `holidays` java collection"
  [holidays all-holidays type]
  (->> all-holidays
       (filter #(instance? type %))
       (.addAll holidays))
  nil)

(s/fdef add-holidays!
  :args (s/and (s/cat :holidays java-collection?
                      :all-holidays (s/coll-of holiday?)
                      :type #(.isAssignableFrom Holiday %)))
  :ret nil?)


(defmacro dispatch-holidays
  "Sort Holiday objects by `types` into the corresponding slots
  of a Holidays object."
  [holidays & types]
  (let [h (gensym "holidays")]
    `(let [~h ~holidays]
       (doto (Holidays.)
         ~@(for [t types]
             `(-> (~(symbol (str ".get" t))) (add-holidays! ~h ~t)))))))

(s/fdef dispatch-holidays
  :args (s/cat :holidays any? :types (s/* simple-symbol?))
  :ret any?)


(defn ->Holidays
  "Parse a collection of holiday configuration edn into a Jollyday
  Holidays configuration bean."
  [config]
  (let [holidays (map ->Holiday config)]
    (doto
        (dispatch-holidays holidays
                           Fixed RelativeToFixed RelativeToWeekdayInMonth
                           ChristianHoliday IslamicHoliday
                           FixedWeekdayBetweenFixed FixedWeekdayRelativeToFixed
                           HinduHoliday HebrewHoliday EthiopianOrthodoxHoliday
                           RelativeToEasterSunday)
      (-> (.getFixedWeekday) (add-holidays! holidays FixedWeekdayInMonth)))))

(s/fdef ->Holidays
  :args (s/cat :config ::holidays)
  :ret #(instance? Holidays %))


(declare add-sub-configurations!)

(defn ->Configuration
  "Parse an edn `config` (top-level or for a subdivision)
  into an Jollyday Configuration bean."
  [config]
  (doto (Configuration.)
    (.setDescription (-> config :description))
    (.setHierarchy (-> config :hierarchy name))
    (.setHolidays (-> config :holidays ->Holidays))
    (add-sub-configurations! config)))

(s/fdef ->Configuration
  :args (s/cat :config ::configuration)
  :ret #(instance? Configuration %))


(defn add-sub-configurations!
  "Parse the configurations for geographical subdivisions from the edn `config`
  and add them to the Jollyday `configuration` bean"
  [configuration config]
  (when-let [sub-configurations (some->> config :sub-configurations (map ->Configuration))]
    (-> configuration .getSubConfigurations (.addAll sub-configurations))
    nil))

(s/fdef add-sub-configurations!
  :args (s/cat :configuration #(instance? Configuration %) :config ::configuration)
  :ret nil?)


;; Fixed day

(defmethod holiday-spec :fixed [_]
  (s/merge
   `holiday-tag-common
   ::date))


(defn ->Fixed
  "Create a Jollyday fixed holiday configuration bean from an edn map"
  [config]
  (doto (Fixed.)
    (set-common-holiday-attributes! config)
    (.setMonth (-> config :month (->enum Month)))
    (.setDay (-> config :day int))
    (add-moving-conditions! config)))

(s/fdef ->Fixed
  :args (s/cat :config ::date)
  :ret #(instance? Fixed %))


(defmethod ->Holiday :fixed
  [config]
  (->Fixed config))


;; Weekday relative to fixed

(defmethod holiday-spec :relative-to-fixed [_]
  (s/merge
   `holiday-tag-common
   (s/and
    (s/keys :req-un [::when ::date] :opt-un [::days ::weekday])
    #(some % [:days :weekday]))))


(defmethod ->Holiday :relative-to-fixed
  [config]
  (doto (RelativeToFixed.)
    (set-common-holiday-attributes! config)
    (.setWeekday (some-> config :weekday (->enum Weekday)))
    (.setWhen (-> config :when (->enum When)))
    (.setDate (-> config :date ->Fixed))
    (.setDays (some-> config :days int))))


(defmethod holiday-spec :fixed-weekday-between-fixed [_]
  (s/merge
   `holiday-tag-common
   (s/keys :req-un [::weekday ::from ::to])))


(defmethod ->Holiday :fixed-weekday-between-fixed
  [config]
  (doto (FixedWeekdayBetweenFixed.)
    (set-common-holiday-attributes! config)
    (.setWeekday (some-> config :weekday (->enum Weekday)))
    (.setFrom (-> config :from ->Fixed))
    (.setTo (-> config :to ->Fixed))))


;; Weekday in month

(s/def ::fixed-weekday
  (s/keys :req-un [::which ::weekday ::month]))


(defmethod holiday-spec :fixed-weekday [_]
  (s/merge
   `holiday-tag-common
   ::fixed-weekday))


(defn ->FixedWeekday
  [config]
  (doto (FixedWeekdayInMonth.)
    (set-common-holiday-attributes! config)
    (.setWhich (-> config :which (->enum Which)))
    (.setWeekday (-> config :weekday (->enum Weekday)))
    (.setMonth (-> config :month (->enum Month)))))

(s/fdef ->FixedWeekday
  :args (s/cat :config ::fixed-weekday)
  :ret #(instance? FixedWeekdayInMonth %))


(defmethod ->Holiday :fixed-weekday
  [config]
  (->FixedWeekday config))


;; Relative to weekday in month

(defmethod holiday-spec :relative-to-weekday-in-month [_]
  (s/merge
   `holiday-tag-common
   (s/keys :req-un [::weekday ::when ::fixed-weekday])))


(defmethod ->Holiday :relative-to-weekday-in-month
  [config]
  (doto (RelativeToWeekdayInMonth.)
    (set-common-holiday-attributes! config)
    (.setWeekday (-> config :weekday (->enum Weekday)))
    (.setWhen (-> config :when (->enum When)))
    (.setFixedWeekday (-> config :fixed-weekday ->FixedWeekday))))


;; Weekday relative to fixed day

(defmethod holiday-spec :fixed-weekday-relative-to-fixed [_]
  (s/merge
   `holiday-tag-common
   (s/keys :req-un [::which ::weekday ::when ::date])))


(defmethod ->Holiday :fixed-weekday-relative-to-fixed
  [config]
  (doto (FixedWeekdayRelativeToFixed.)
    (set-common-holiday-attributes! config)
    (.setWhich (-> config :which (->enum Which)))
    (.setWeekday (-> config :weekday (->enum Weekday)))
    (.setWhen (-> config :when (->enum When)))
    (.setDay (-> config :date ->Fixed))))


;; Christian

(s/def :christian/type
  #{:good-friday :easter-monday :ascension-day :whit-monday :corpus-christi
    :maundy-thursday :ash-wednesday :mardi-gras :general-prayer-day
    :clean-monday :shrove-monday :pentecost :carnival :easter-saturday
    :easter-tuesday :sacred-heart :easter :pentecost-monday :whit-sunday})


(s/def ::chronology #{:julian :gregorian})


(defmethod holiday-spec :christian-holiday [_]
  (s/merge
   `holiday-tag-common
   (s/keys :req-un [:christian/type] :opt-un [::chronology ::moving-conditions])))


(defmethod ->Holiday :christian-holiday
  [config]
  (doto (ChristianHoliday.)
    (set-common-holiday-attributes! config)
    (.setType (-> config :type (->enum ChristianHolidayType)))
    (.setChronology (some-> config :chronology (->enum ChronologyType)))
    (add-moving-conditions! config)))


(defmethod holiday-spec :relative-to-easter-sunday [_]
  (s/merge
   `holiday-tag-common
   (s/keys :req-un [::chronology ::days])))


(defmethod ->Holiday :relative-to-easter-sunday
  [config]
  (doto (RelativeToEasterSunday.)
    (set-common-holiday-attributes! config)
    (.setChronology (some-> config :chronology (->enum ChronologyType)))
    (.setDays (-> config :days int))))


;; Islamic

(s/def :islamic/type
  #{:newyear :aschura :mawlid-an-nabi :lailat-al-miraj :lailat-al-barat
    :ramadan :lailat-al-qadr :id-al-fitr :id-ul-adha})


(defmethod holiday-spec :islamic-holiday [_]
  (s/merge
   `holiday-tag-common
   (s/keys :req-un [:islamic/type])))


(defmethod ->Holiday :islamic-holiday
  [config]
  (doto (IslamicHoliday.)
    (set-common-holiday-attributes! config)
    (.setType (-> config :type (->enum IslamicHolidayType)))))


;; Hindu

(s/def :hindu/type #{:holi})


(defmethod holiday-spec :hindu-holiday [_]
  (s/merge
   `holiday-tag-common
   (s/keys :req-un [:hindu/type])))


(defmethod ->Holiday :hindu-holiday
  [config]
  (doto (HinduHoliday.)
    (set-common-holiday-attributes! config)
    (.setType (-> config :type (->enum HinduHolidayType)))))


;; Hebrew

(s/def :hebrew/type
  #{:rosh-hashanah :aseret-yemei-teshuva :yom-kippur :sukkot :shemini-atzeret
    :hanukkah :asarah-betevet :tu-bishvat :purim :1-nisan :pesach :sefirah
    :lag-baomer :shavout :17-tammuz :tisha-bav :1-elul :rosh-codesh :shabbat
    :yom-hashoah :yom-hazikaron :yom-haatzamaut :yom-yerushalaim})


(defmethod holiday-spec :hebrew-holiday [_]
  (s/merge
   `holiday-tag-common
   (s/keys :req-un [:hebrew/type])))


(defmethod ->Holiday :hebrew-holiday
  [config]
  (doto (HebrewHoliday.)
    (set-common-holiday-attributes! config)
    (.setType (-> config :type ->const-name))))


;; Ethiopian orthodox

(s/def :ethiopian-orthodox/type
  #{:timkat :enkutatash :meskel})


(defmethod holiday-spec :ethiopian-orthodox-holiday [_]
  (s/merge
   `holiday-tag-common
   (s/keys :req-un [:ethiopian-orthodox/type])))


(defmethod ->Holiday :ethiopian-orthodox-holiday
  [config]
  (doto (EthiopianOrthodoxHoliday.)
    (set-common-holiday-attributes! config)
    (.setType (-> config :type (->enum EthiopianOrthodoxHolidayType)))))


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
