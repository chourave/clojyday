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
   [clojyday.place :as place]
   [clojyday.util :as util])
  (:import
    (de.jollyday ManagerParameter)
    (de.jollyday.config ChristianHoliday ChristianHolidayType ChronologyType Configuration
                        EthiopianOrthodoxHoliday EthiopianOrthodoxHolidayType
                        Fixed FixedWeekdayBetweenFixed FixedWeekdayInMonth
                        FixedWeekdayRelativeToFixed HebrewHoliday HinduHoliday
                        HinduHolidayType Holiday HolidayType Holidays IslamicHoliday
                        IslamicHolidayType Month MovingCondition RelativeToEasterSunday
                        RelativeToFixed RelativeToWeekdayInMonth Weekday When Which With)
    (de.jollyday.datasource ConfigurationDataSource)
    (de.jollyday.parameter CalendarPartManagerParameter)
    (java.io PushbackReader)
    (de.jollyday.util ResourceUtil)))

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


(s/def ::calendaer-name named?)

;; XML reading

(s/def :xml/tag keyword?)

(s/def :xml/attrs (s/nilable (s/map-of keyword? string?)))

(s/def :xml/content (s/cat :first-text (s/? string?)
                           :nodes-and-text (s/* (s/cat :node `xml-node
                                                       :text (s/? string?)))))

(s/def xml-node
  (s/keys :req-un [:xml/tag :xml/attrs :xml/content]))


(defn read-xml
  "Read a Jollyday XML calendar configuration file for the given locale
  and parse it to a xml map"
  [suffix]
  (-> (str "holidays/Holidays_" (name suffix) ".xml")
      io/resource
      io/input-stream
      xml/parse))

(s/fdef read-xml
  :args (s/cat :suffix ::calendar-name)
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


(defn ->const-name
  "Parse a :clojure-keyword to a JAVA_CONSTANT_NAME (as a strig)"
  [x]
  (-> x name string/upper-case (string/replace #"-" "_")))

(s/fdef ->const-name
  :args (s/cat :x named?)
  :ret (s/and string? util/uppercase?))


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
       (if-let [v (attribute node (keyword (util/kebab->camel att)))]
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
  :fn #(util/equals-ignore-case?
        (-> % :ret name (util/strip "-"))
        (-> % :args :s (util/strip "_"))))


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
  "Parse the xml tag name of a holiday type to a holiday type keyword.
  The xml namespace is discarded."
  [tag]
  (-> tag
      name
      (string/split #":")
      fnext
      util/camel->kebab
      keyword))

(s/fdef tag->holiday
  :args (s/cat :tag :xml/tag)
  :ret ::holiday)


(defn parse-common-holiday-attributes
  "Parse an xml node, reading the attributes that are common to all holiday types,
  and return them as a map."
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
  :ret `holiday-tag-common)


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


(defn parse-holiday
  "Parse an xml node describing a holiday to an edn holiday description"
  [node]
  (merge
   (parse-common-holiday-attributes node)
   (-parse-holiday node)))

(s/fdef parse-holiday
  :args (s/cat :node `xml-node)
  :ret `holiday)


(defn parse-configuration
  "Parse an xml holiday configuration to an edn configuration"
  [configuration]
  (let [holidays           (element configuration :Holidays)
        sub-configurations (elements configuration :SubConfigurations)
        configuration      {:description (attribute configuration :description)
                            :hierarchy   (-> configuration (attribute :hierarchy) ->keyword)
                            :holidays    (mapv parse-holiday (:content holidays))}]
    (if sub-configurations
      (assoc configuration :sub-configurations (mapv parse-configuration sub-configurations))
      configuration)))


(s/fdef parse-configuration
  :args (s/cat :configuration `xml-node)
  :ret ::configuration)


(defn read-configuration
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and parse it to an edn configuration.

  Example: (read-configuration :fr)"
  [calendar-name]
  (parse-configuration (read-xml calendar-name)))

(s/fdef read-configuration
  :args (s/cat :calendar-name ::calendar-name)
  :ret ::configuration)


(def key-order
  "Order in which keys should appear in the final edn configuration (purely for readability)"
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
  "Make a copy of map `m`, with keys in `key-order`.
  Throw an exception if `m` contains unknown keys."
  [m]
  (let [ks (-> m keys set)
        ordered-keys (filter ks key-order)
        unhandled-keys (remove (set ordered-keys) ks)]
    (when (pos? (count unhandled-keys))
      (throw (Exception. (str "Unhandled keys " (string/join ", " unhandled-keys)))))
    (apply array-map (mapcat #(vector % (% m)) ordered-keys))))

(s/fdef sort-map
  :args (s/cat :m map?)
  :ret (s/map-of (set key-order) any?)
  :fn #(= (-> % :args :m)
          (-> % :ret)))


(defn sorted-configuration
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and parse it to an edn configuration, sorting
  keys to make it easier to read for humans.

  Example: (sorted-configuration :fr)"
  [calendar-name]
  (try
    (->> calendar-name
         read-configuration
         (postwalk #(if (map? %) (sort-map %) %)))
    (catch Exception e
      (throw (Exception. (str "While reading calendar " (name calendar-name))
                         e)))))

(s/fdef sorted-configuration
  :args (s/cat :calendar-name ::calendar-name)
  :ret ::configuration)


(defn fast-print
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and print is as edn to the `writer`, with emphasis
  on the speed of the conversion."
  [calendar-name writer]
  (binding [*out* writer]
    (prn (read-configuration calendar-name))))

(s/fdef fast-print
  :args (s/cat :calendar-name ::calendar-name, :writer #(instance? java.io.Writer %))
  :ret nil?)


(defn pretty-print
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and print is as edn to the `writer`, with emphasis
  on a nice-looking output."
  [calendar-name writer]
  (pprint (sorted-configuration calendar-name) writer))

(s/fdef pretty-print
  :args (s/cat :calendar-name ::calendar-name, :writer #(instance? java.io.Writer %))
  :ret nil?)


(defn cal-edn-path
  "Path for the edn configuration file for a given `calendar-name`"
  [calendar-name]
  (io/file "holidays" (str (name calendar-name) "-holidays.edn")))

(s/fdef cal-edn-path
  :args (s/cat :cal ::calendar-name)
  :ret #(instance? java.io.File %))


(defn xml->edn
  "Convert the calendar named `calendar-name` from an xml file in the Jollyday
  distribution to an edn file in `target`. `print` should be either
  `pretty-print` or `fast-print`."
  [target-dir print calendar-name]
  (binding [pprint/*print-right-margin* 110]
    (let [f (io/file target-dir (cal-edn-path calendar-name))]
      (io/make-parents f)
      (print calendar-name (io/writer f)))))

(s/fdef xml->edn
  :args (s/cat :target-dir string?, :print fn?, :calendar-name ::calendar-name)
  :ret nil?)

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


(place/add-format :xml-clj :xml)
(place/add-format :edn)


(defmethod place/configuration-data-source :xml-clj
  [_]
  (reify
    ConfigurationDataSource
    (getConfiguration [_ parameters]
      (-> ^ManagerParameter parameters
          .createResourceUrl
          io/input-stream
          xml/parse
          parse-configuration
          ->Configuration))))


(defmethod place/configuration-data-source :edn
  [_]
  (reify
    ConfigurationDataSource
    (getConfiguration [_ parameters]
      (-> ^ManagerParameter parameters
          .createResourceUrl
          io/reader
          PushbackReader.
          edn/read
          ->Configuration))))


(defmethod place/-create-manager-parameters [String :edn]
  [calendar-part _]
  (proxy [CalendarPartManagerParameter] [(place/normalized-calendar-part calendar-part) nil]
    (createResourceUrl []
      (->> calendar-part
           cal-edn-path
           str
          (.getResource (ResourceUtil.))))))


;; Fixed day

(defn parse-fixed
  "Parse a fixed day and month holiday from an xml node to an edn map"
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


(defmethod -parse-holiday :tns:FixedWeekdayBetweenFixed [node]
  (-> node
      (parse-attributes
       {:weekday ->keyword})
      (assoc :from (-> node (element :from) parse-fixed))
      (assoc :to (-> node (element :to) parse-fixed))))

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

(defn parse-fixed-weekday
  "Parse a fixed weekday of month from an xml node to an edn map"
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

(defmethod -parse-holiday :tns:RelativeToWeekdayInMonth [node]
  (-> node
      (parse-attributes
       {:weekday ->keyword
        :when    ->keyword})
      (assoc :fixed-weekday (-> node (element :FixedWeekday) parse-fixed-weekday))))

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

(defmethod -parse-holiday :tns:FixedWeekdayRelativeToFixed [node]
  (-> node
      (parse-attributes
       {:which   ->keyword
        :weekday ->keyword
        :when    ->keyword})
      (assoc :date (-> node (element :day) parse-fixed))))

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

(defmethod -parse-holiday :tns:ChristianHoliday [node]
  (merge
   (parse-attributes
    node
    {:type       ->keyword
     :chronology ->keyword})
   (parse-moving-conditions node)))

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


(defmethod -parse-holiday :tns:RelativeToEasterSunday [node]
  {:chronology (-> node (element :chronology) :content first ->keyword)
   :days       (-> node (element :days) :content first ->int)})


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


(defmethod -parse-holiday :tns:IslamicHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

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

(defmethod -parse-holiday :tns:HinduHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

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

(defmethod -parse-holiday :tns:HebrewHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

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

(defmethod -parse-holiday :tns:EthiopianOrthodoxHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

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
