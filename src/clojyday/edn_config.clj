;; Copyright and license information at end of file

(ns clojyday.edn-config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.walk :refer [postwalk]]
   [clojure.xml :as xml]
   [clojyday.place :as place])
  (:import
   (de.jollyday ManagerParameter)
   (de.jollyday.config ChristianHoliday ChristianHolidayType ChronologyType Configuration
                       EthiopianOrthodoxHoliday EthiopianOrthodoxHolidayType
                       Fixed FixedWeekdayBetweenFixed FixedWeekdayInMonth
                       FixedWeekdayRelativeToFixed HebrewHoliday HinduHoliday
                       HinduHolidayType HolidayType Holidays IslamicHoliday
                       IslamicHolidayType Month MovingCondition RelativeToEasterSunday
                       RelativeToFixed RelativeToWeekdayInMonth Weekday When Which With)
   (de.jollyday.datasource ConfigurationDataSource)
   (de.jollyday.parameter CalendarPartManagerParameter)
   (java.io PushbackReader)))

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

;; XML reading

(s/def :xml/tag keyword?)

(s/def :xml/attrs (s/nilable (s/map-of :key :string)))

(s/def :xml/content (s/cat :first-text (s/? string?)
                           :nodes-and-text (s/* (s/cat :node `xml-node
                                                       :text (s/? string?)))))

(s/def xml-node
  (s/keys req-un [:xml/tag :xml/attrs :xml/content]))


(defn read-xml
  "Read a Jollyday XML calendar configuration file for the given locale
  and parse it to a xml map"
  [suffix]
  (-> (str "holidays/Holidays_" (name suffix) ".xml")
      io/resource
      io/input-stream
      xml/parse))

(s/fdef read-xml
  :args (s/cat :suffix #(instance? clojure.lang.Named %))
  :ret `xml-node)


(defn attribute
  "Get the value of the named attribute in an xml node"
  [node attribute-name]
  (get-in node [:attrs attribute-name]))

(s/fdef attribute
  :args (s/cat :node `xml-node :attribute-name keyword?)
  :ret (s/nilable string?))


(defn elements
  "Get the child elements with a given tag of an xml node"
  [node tag]
  (let [prefixed (keyword (str "tns" tag))]
    (->> node
         :content
         (filter #(= prefixed (:tag %)))
         seq)))

(s/fdef elements
  :args (s/cat :node `xml-node :tag keyword?)
  :ret (s/nilable (s/coll-of `xml-node)))


(defn element
  "Get the first child element with a given tag of an xml node"
  [node tag]
  (first (elements node tag)))

(s/fdef element
  :args (s/cat :node `xml-node :tag keyword?)
  :ret (s/nilable `xml-node))


;; String manipulation

(defn equals-ignore-case?
  "Case insensitive string comparison"
  [s1 s2]
  (= (-> s1 string/lower-case)
     (-> s2 string/lower-case)))

(s/fdef equals-ignore-case?
  :args (s/cat :s1 string? :s2 string?)
  :ret boolean?)


(defn lowercase?
  "Does `s` contain no upper case characters?"
  [s]
  (= s (string/lower-case s)))

(s/fdef lowercase?
  :args (s/cat :s string?)
  :ret boolean?)


(defn strip
  "Remvoe any occurrences of `to-strip` in `s`"
  [s to-strip]
  (string/replace s to-strip ""))

(s/fdef strip
  :args (s/cat :s string? :to-strip string?)
  :ret string?)


(defn kebab->camel
  "Turn kebab-case strings into camelCase"
  [s]
  (let [[head & tail] (string/split s #"-")]
    (apply str
           head
           (map string/capitalize tail))))

(s/fdef kebab->camel
  :args (s/cat :s string?)
  :ret string?
  :fn #(equals-ignore-case?
        (:ret %)
        (-> % :args :s (strip "-"))))


(defn camel->kebab
  "Turn camelCase (and PascalCase) strings into kebab-case"
  [s]
  (as-> s %
    (string/split % #"(?=[A-Z])")
    (string/join \- %)
    (string/lower-case %)))

(s/fdef camel->kebab
  :args (s/cat :s string?)
  :ret string?
  :fn (s/and
       #(-> % :ret lowercase?)
       #(equals-ignore-case?
         (-> % :ret (strip "-"))
         (-> % :args :s))))


(defn ->const-name
  "Parse a :clojure-keyword to a JAVA_CONSTANT_NAME (as a strig)"
  [x]
  (-> x name string/upper-case (string/replace #"-" "_")))


;;

(defn parse-attributes
  "Parse selected attributes from an xml node.

  `attribute-fns` should be a map from attribute names to functions
  that parse attribute values. The attributes names should be kebab-cased
  keywords, and will be translated to camelCase when looking them up in
  the node.

  The return value is a map of kebab-cased attribute names to values returned
  by the parsing function, for those attributes actually present in the node.
  "
  [node attribute-fns]
  (reduce
   (fn [res [att f]]
     (let [att (name att)]
       (if-let [v (attribute node (keyword (kebab->camel att)))]
         (assoc res
                (keyword att)
                (f v))
         res)))
   {}
   attribute-fns))

(s/fdef parse-attributes
  :args (s/cat :node `xml-node
               :attribute-fns (s/map-of keyword? ifn?))
  :ret (s/map-of keyword? any?))


(defn ->int
  "Parse a string to an integer"
  [s]
  (Integer/parseInt s))

(s/fdef ->int
  :args (s/cat :s string?)
  :ret int?)


(defn ->keyword
  "parse a CONSTANT_CASE string to a :kebab-keyword"
  [s]
  (-> s
   (string/replace "_" "-")
   string/lower-case
   keyword))

(s/fdef ->keyword
  :args (s/cat :s string?)
  :ret keyword?
  :fn #(equals-ignore-case?
        (-> % :ret name (strip "-"))
        (-> % :args :s (strip "_"))))


(defmacro ->enum
  "Parse a keyword to a value in a Java Enum"
  [value enum]
  `(-> ~value ->const-name (~(symbol (str enum) "valueOf"))))


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
  (s/keys
   :req-un [::holiday]
   :opt-un [::valid-from ::valid-to ::every ::description-key ::localized-type]))

(defn parse-moving-conditions
  "Parse the moving conditions from an xml node into a map"
  [node]
  (when-let [conditions (elements node :MovingCondition)]
    {:moving-conditions
     (map #(parse-attributes
            %
            {:substitute ->keyword
             :with       ->keyword
             :weekday    ->keyword})
          conditions)}))

(s/fdef parse-moving-conditions
  :args (s/cat :node `xml-node)
  :ret (s/nilable (s/keys :req-un [::moving-conditions])))


(defn add-moving-conditions
  "Add the moving conditions from a map to a Jollyday Holiday object"
  [bean config]
  (-> bean
      (.getMovingCondition)
      (.addAll (map #(doto (MovingCondition.)
                       (.setSubstitute (-> % :substitute (->enum Weekday)))
                       (.setWith (-> % :with (->enum With)))
                       (.setWeekday (-> % :weekday (->enum Weekday))))
                    (:moving-conditions config)))))


(defmulti -parse-holiday
  "Parse the tag-specific parts of an xml node to a map.
  Do not use directly, use `parse-holiday` instead (it also
  handles the common parts)"
  :tag)

(defmulti holiday-spec
  "Returns the spec for a holiday definition"
  :holiday)

(s/def holiday (s/multi-spec holiday-spec :holiday))

(defn tag->holiday
  ""
  [tag]
  (-> tag
      name
      (string/split #":")
      fnext
      camel->kebab
      keyword))

(s/fdef tag->holiday
  :args (s/cat :tag :xml/tag)
  :ret ::holiday)


(defn parse-common-holiday-attributes
  ""
  [node]
  (let [description-key (attribute node :descriptionPropertiesKey)
        holiday         (-> node
                            (parse-attributes
                             {:valid-from      ->int
                              :valid-to        ->int
                              :every           ->keyword
                              :localized-type  ->keyword})
                            (assoc :holiday (-> node :tag tag->holiday)))]
    (if description-key
      (assoc holiday :description-key (->keyword description-key))
      holiday)))

(s/fdef parse-common-holiday-attributes
  :args (s/cat :node `xml-node)
  :ret `holiday-common)


(defn set-common-holiday-attributes
  [holiday config]
  (doto holiday
    (.setValidFrom (some-> config :valid-from int))
    (.setValidTo (some-> config :valid-to int))
    (.setEvery (some-> config :every ->const-name))
    (.setLocalizedType (some-> config :localized-type (->enum HolidayType)))
    (.setDescriptionPropertiesKey (some-> config :description-key ->const-name))))


(defn parse-holiday
  ""
  [node]
  (merge
   (parse-common-holiday-attributes node)
   (-parse-holiday node)))

(s/fdef parse-holiday
  :args (s/cat :node `xml-node)
  :ret `holiday)


(defn parse-configuration
  ""
  [configuration]

  (let [holidays           (element configuration :Holidays)
        sub-configurations (elements configuration :SubConfigurations)
        configuration      {:description (attribute configuration :description)
                            :hierarchy   (-> configuration (attribute :hierarchy) ->keyword)
                            :holidays    (mapv parse-holiday (:content holidays))}]
    (if sub-configurations
      (assoc configuration :sub-configurations (mapv parse-configuration sub-configurations))
      configuration)))


(defn read-configuration
  ""
  [calendar]
  (parse-configuration (read-xml calendar)))


(def key-order
  ""
  [;; configuration
   :hierarchy, :description, :holidays, :sub-configurations

   ;; holiday prefix
   :holiday

   ;; fixed
   :month, :day

   ;; relative prefix
   :when, :date, :days

   ;; weekday
   :which, :weekday, :from, :to

   ;; relative suffix
   :fixed-weekday, :every

   ;; christian / islamic / hebrew / hindu / ethiopian orthodox
   :type, :chronology

   ;; holiday suffix
   :valid-from, :valid-to, :description-key, :localized-type, :moving-conditions

   ;; moving conditions
   :substitute, :with])


(defn sort-map
  ""
  [m]
  (let [ks (-> m keys set)
        ordered-keys (filter ks key-order)
        unhandled-keys (remove (set ordered-keys) ks)]
    (when (pos? (count unhandled-keys))
      (throw (Exception. (str "Unhandled keys " (string/join ", " unhandled-keys)))))
    (apply array-map (mapcat #(vector % (% m)) ordered-keys))))


(defn sorted-configuration
  ""
  [calendar-name]
  (try
    (->> calendar-name
         read-configuration
         (postwalk #(if (map? %) (sort-map %) %)))
    (catch Exception e
      (throw (Exception. (str "While reading calendat " (name calendar-name))
                         e)))))


(defn raw-print
  ""
  [config w]
  (binding [*out* w]
    (prn (read-configuration config))))


(defn pretty-print
  ""
  [config w]
  (pprint (sorted-configuration config) w))


(defn cal-edn-path
  ""
  [cal]
  (io/file "holidays" (str (name cal) "-holidays.edn")))


(defn xml->edn
  ""
  [target-dir print cal]
  (binding [pprint/*print-right-margin* 110]
    (let [f (io/file target-dir (cal-edn-path cal))]
      (io/make-parents f)
      (print cal (io/writer f)))))


;;

(defmulti ->Holiday
  ""
  :holiday)


(defn add-holidays
  ""
  [holidays all-holidays type]
  (->> all-holidays
       (filter #(instance? type %))
       (.addAll holidays)))


(defmacro dispatch-holidays
  ""
  [holidays & types]
  (let [h (gensym "holidays")]
    `(let [~h ~holidays]
       (doto (Holidays.)
         ~@(for [t types]
             `(-> (~(symbol (str ".get" t))) (add-holidays ~h ~t)))))))


(defn ->Holidays
  ""
  [config]
  (let [holidays (map ->Holiday config)]
    (doto
        (dispatch-holidays holidays
                           Fixed RelativeToFixed RelativeToWeekdayInMonth
                           ChristianHoliday IslamicHoliday
                           FixedWeekdayBetweenFixed FixedWeekdayRelativeToFixed
                           HinduHoliday HebrewHoliday EthiopianOrthodoxHoliday
                           RelativeToEasterSunday)
      (-> (.getFixedWeekday) (add-holidays holidays FixedWeekdayInMonth)))))


(declare add-sub-configurations!)


(defn ->Configuration
  ""
  [config]
  (doto (Configuration.)
    (.setDescription (-> config :description))
    (.setHierarchy (-> config :hierarchy name))
    (.setHolidays (-> config :holidays ->Holidays))
    (add-sub-configurations! config)))


(defn add-sub-configurations!
  ""
  [configuration config]
  (when-let [sub-configurations (some->> config :sub-configurations (map ->Configuration))]
    (-> configuration .getSubConfigurations (.addAll sub-configurations))))


(def edn-configuration-format
  "Reads a Clojyday EDN calendar configuration file
  to a Jollyday configuration object"
  (reify
    ConfigurationDataSource
    (getConfiguration [_ parameters]
      (-> ^ManagerParameter parameters
          .createResourceUrl
          io/reader
          PushbackReader.
          edn/read
          ->Configuration))

    place/ConfigurationFormat
    (configuration-data-source [this _]
      this)))


(def xml-configuration-format
  "Reads a Jollyday XML calendar configuration file
  to a Jollyday configuration object, without relying on JAXB"
  (reify
    ConfigurationDataSource
    (getConfiguration [_ parameters]
      (-> ^ManagerParameter parameters
          .createResourceUrl
          io/input-stream
          xml/parse
          parse-configuration
          ->Configuration))

    place/ConfigurationFormat
    (configuration-data-source [this _]
      this)))


;; Fixed day

(defn parse-fixed
  ""
  [node]
  (merge
   (parse-attributes
    node
    {:month ->keyword
     :day   ->int})
   (parse-moving-conditions node)))

(s/fdef parse-fixed
  :args (s/cat :node `xml-node)
  :ret ::date)


(defmethod -parse-holiday :tns:Fixed [node]
  (parse-fixed node))

(defmethod holiday-spec :fixed [_]
  (s/merge
   `holiday-common
   ::date))


(defn ->Fixed
  ""
  [config]
  (doto (Fixed.)
    (set-common-holiday-attributes config)
    (.setMonth (-> config :month (->enum Month)))
    (.setDay (-> config :day int))
    (add-moving-conditions config)))


(defmethod ->Holiday :fixed
  [config]
  (->Fixed config))

;; Weekday relative to fixed

(defmethod -parse-holiday :tns:RelativeToFixed [node]
  (let [weekday (-> node (element :Weekday) :content first)
        days    (-> node (element :Days) :content first)
        holiday {:when (-> node (element :When) :content first ->keyword)
                 :date (-> node (element :Date) parse-fixed)}]
    (cond-> holiday
      weekday (assoc :weekday (->keyword weekday))
      days    (assoc :days (->int days)))))

(defmethod holiday-spec :relative-to-fixed [_]
  (s/merge
   `holiday-common
   (s/and
    (s/keys :req-un [::when ::date] :opt-un [::days ::weekday])
    #(some % [:days :weekday]))))


(defmethod ->Holiday :relative-to-fixed
  [config]
  (doto (RelativeToFixed.)
    (set-common-holiday-attributes config)
    (.setWeekday (some-> config :weekday (->enum Weekday)))
    (.setWhen (-> config :when (->enum When)))
    (.setDate (-> config :date ->Fixed))
    (.setDays (some-> config :days int))))


(defmethod -parse-holiday :tns:FixedWeekdayBetweenFixed [node]
  (-> node
      (parse-attributes
       {:weekday ->keyword})
      (assoc :from (-> node (element :from) parse-fixed))
      (assoc :to (-> node (element :to) parse-fixed))))

(defmethod holiday-spec :fixed-weekday-between-fixed [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [::weekday ::from ::to])))


(defmethod ->Holiday :fixed-weekday-between-fixed
  [config]
  (doto (FixedWeekdayBetweenFixed.)
    (set-common-holiday-attributes config)
    (.setWeekday (some-> config :weekday (->enum Weekday)))
    (.setFrom (-> config :from ->Fixed))
    (.setTo (-> config :to ->Fixed))))


;; Weekday in month

(s/def ::fixed-weekday
  (s/keys :req-un [::which ::weekday ::month]))

(defn parse-fixed-weekday
  ""
  [node]
  (parse-attributes
   node
   {:which   ->keyword
    :weekday ->keyword
    :month   ->keyword}))

(s/fdef parse-fixed-weekday
  :args (s/cat :node `xml-node)
  :ret ::fixed-weekday)


(defmethod -parse-holiday :tns:FixedWeekday [node]
  (parse-fixed-weekday node))

(defmethod holiday-spec :fixed-weekday [_]
  (s/merge
   `holiday-common
   ::fixed-weekday))


(defn ->FixedWeekday
  [config]
  (doto (FixedWeekdayInMonth.)
    (set-common-holiday-attributes config)
    (.setWhich (-> config :which (->enum Which)))
    (.setWeekday (-> config :weekday (->enum Weekday)))
    (.setMonth (-> config :month (->enum Month)))))


(defmethod ->Holiday :fixed-weekday
  [config]
  (->FixedWeekday config))

;; Relative to weekday in month

(defmethod -parse-holiday :tns:RelativeToWeekdayInMonth [node]
  (-> node
      (parse-attributes
       {:weekday ->keyword
        :when    ->keyword})
      (assoc :fixed-weekday (-> node (element :FixedWeekday) parse-fixed-weekday))))

(defmethod holiday-spec :relative-to-weekday-in-month [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [::weekday ::when ::fixed-weekday])))


(defmethod ->Holiday :relative-to-weekday-in-month
  [config]
  (doto (RelativeToWeekdayInMonth.)
    (set-common-holiday-attributes config)
    (.setWeekday (-> config :weekday (->enum Weekday)))
    (.setWhen (-> config :when (->enum When)))
    (.setFixedWeekday (-> config :fixed-weekday ->FixedWeekday))))


;; Weekday relative to fixed day

(defmethod -parse-holiday :tns:FixedWeekdayRelativeToFixed [node]
  (-> node
      (parse-attributes
       {:which   ->keyword
        :weekday ->keyword
        :when    ->keyword})
      (assoc :date (-> node (element :day) parse-fixed))))

(defmethod holiday-spec :fixed-weekday-relative-to-fixed [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [::which ::weekday ::when ::date])))


(defmethod ->Holiday :fixed-weekday-relative-to-fixed
  [config]
  (doto (FixedWeekdayRelativeToFixed.)
    (set-common-holiday-attributes config)
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

(defmethod -parse-holiday :tns:ChristianHoliday [node]
  (merge
   (parse-attributes
    node
    {:type       ->keyword
     :chronology ->keyword})
   (parse-moving-conditions node)))

(defmethod holiday-spec :christian-holiday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [:christian/type] :opt-un [::chronology ::moving-conditions])))


(defmethod ->Holiday :christian-holiday
  [config]
  (doto (ChristianHoliday.)
    (set-common-holiday-attributes config)
    (.setType (-> config :type (->enum ChristianHolidayType)))
    (.setChronology (some-> config :chronology (->enum ChronologyType)))
    (add-moving-conditions config)))


(defmethod -parse-holiday :tns:RelativeToEasterSunday [node]
  {:chronology (-> node (element :chronology) :content first ->keyword)
   :days       (-> node (element :days) :content first ->int)})


(defmethod holiday-spec :relative-to-easter-sunday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [::chronology ::days])))


(defmethod ->Holiday :relative-to-easter-sunday
  [config]
  (doto (RelativeToEasterSunday.)
    (set-common-holiday-attributes config)
    (.setChronology (some-> config :chronology (->enum ChronologyType)))
    (.setDays (-> config :days int))))


;; Islamic

(s/def :islamic/type
  #{:newyear :aschura :mawlid-an-nabi :lailat-al-miraj :lailat-al-barat
    :ramadan :lailat-al-qadr :id-al-fitr :id-ul-adha})


(defmethod -parse-holiday :tns:IslamicHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

(defmethod holiday-spec :islamic-holiday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [:islamic/type])))


(defmethod ->Holiday :islamic-holiday
  [config]
  (doto (IslamicHoliday.)
    (set-common-holiday-attributes config)
    (.setType (-> config :type (->enum IslamicHolidayType)))))


;; Hindu

(s/def :hindu/type #{:holi})

(defmethod -parse-holiday :tns:HinduHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

(defmethod holiday-spec :hindu-holiday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [:hindu/type])))


(defmethod ->Holiday :hindu-holiday
  [config]
  (doto (HinduHoliday.)
    (set-common-holiday-attributes config)
    (.setType (-> config :type (->enum HinduHolidayType)))))


;; Hebrew

(s/def :hebrew/type
  #{:rosh-hashanah :aseret-yemei-teshuva :yom-kippur :sukkot :shemini-atzeret
    :hanukkah :asarah-betevet :tu-bishvat :purim :1-nisan :pesach :sefirah
    :lag-baomer :shavout :17-tammuz :tisha-bav :1-elul :rosh-codesh :shabbat
    :yom-hashoah :yom-hazikaron :yom-haatzamaut :yom-yerushalaim})

(defmethod -parse-holiday :tns:HebrewHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

(defmethod holiday-spec :hebrew-holiday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [:hebrew/type])))


(defmethod ->Holiday :hebrew-holiday
  [config]
  (doto (HebrewHoliday.)
    (set-common-holiday-attributes config)
    (.setType (-> config :type ->const-name))))


;; Ethiopian orthodox
(s/def :ethiopian-orthodox/type
  #{:timkat :enkutatash :meskel})

(defmethod -parse-holiday :tns:EthiopianOrthodoxHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

(defmethod holiday-spec :ethiopian-orthodox-holiday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [:ethiopian-orthodox/type])))


(defmethod ->Holiday :ethiopian-orthodox-holiday
  [config]
  (doto (EthiopianOrthodoxHoliday.)
    (set-common-holiday-attributes config)
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
