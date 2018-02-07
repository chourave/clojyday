;; Copyright and license information at end of file

(ns clojyday.config.xml
  "Parse the same xml configuration files as Jollyday, but faster,
  and without requiring JAXB, which is getting deprecated as of Java 9."
  (:require
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.xml :as xml]
   [clojyday.config.core :as config]
   [clojyday.place :as place]
   [clojyday.util :as util])

  (:import
    (de.jollyday ManagerParameter)
    (de.jollyday.datasource ConfigurationDataSource)))


;; XML reading

(s/def ::tag keyword?)

(s/def ::attrs (s/nilable (s/map-of keyword? string?)))

(s/def ::content (s/cat :first-text (s/? string?)
                           :nodes-and-text (s/* (s/cat :node `xml-node
                                                       :text (s/? string?)))))

(s/def xml-node
  (s/keys :req-un [::tag ::attrs ::content]))


(defn strip-tag-namespace
  "Discard the namespace prefix from a tag"
  [tag]
  (-> tag name (string/split #":" 2) last))

(s/fdef strip-tag-namespace
  :args (s/cat :tag keyword?)
  :ret  string?)


(defn strip-namespaces
  "Discard the namespace prefixes from all tags in a node
  and its descendants."
  [node]
  (-> node
      (update :tag #(-> % strip-tag-namespace keyword))
      (update :content #(map strip-namespaces %))))

(s/fdef strip-namespaces
  :args (s/cat :node `xml-node)
  :ret `xml-node)


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
  :ret (s/nilable (s/keys :req-un [::config/moving-conditions])))


(defmulti -parse-holiday
  "Parse the tag-specific parts of an xml node to a map.
  Do not use directly, use `parse-holiday` instead (it also
  handles the common parts)"
  :tag)


(defn tag->holiday
  "Parse the xml tag name of a holiday type to a holiday type keyword.
  The xml namespace is discarded."
  [tag]
  (-> tag
      strip-tag-namespace
      util/camel->kebab
      keyword))

(s/fdef tag->holiday
  :args (s/cat :tag ::tag)
  :ret ::config/holiday)


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
  :ret `config/holiday-tag-common)


(defn parse-holiday
  "Parse an xml node describing a holiday to an edn holiday description"
  [node]
  (merge
   (parse-common-holiday-attributes node)
   (-parse-holiday node)))

(s/fdef parse-holiday
  :args (s/cat :node `xml-node)
  :ret `config/holiday)


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
  :ret ::config/configuration)


(defn read-configuration
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and parse it to an edn configuration.

  Example: (read-configuration :fr)"
  [calendar-name]
  (parse-configuration (read-xml calendar-name)))

(s/fdef read-configuration
  :args (s/cat :calendar-name ::config/calendar-name)
  :ret ::config/configuration)


;;

(place/add-format :xml-clj :xml)


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
          config/->Configuration))))


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
  :ret ::config/date)


(defmethod -parse-holiday :tns:Fixed [node]
  (parse-fixed node))


;; Weekday relative to fixed

(defmethod -parse-holiday :tns:RelativeToFixed [node]
  (let [weekday (-> node (element :Weekday) :content first)
        days    (-> node (element :Days) :content first)
        holiday {:when (-> node (element :When) :content first ->keyword)
                 :date (-> node (element :Date) parse-fixed)}]
    (cond-> holiday
      weekday (assoc :weekday (->keyword weekday))
      days    (assoc :days (->int days)))))


(defmethod -parse-holiday :tns:FixedWeekdayBetweenFixed [node]
  (-> node
      (parse-attributes
       {:weekday ->keyword})
      (assoc :from (-> node (element :from) parse-fixed))
      (assoc :to (-> node (element :to) parse-fixed))))


;; Weekday in month

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
  :ret ::config/fixed-weekday)


(defmethod -parse-holiday :tns:FixedWeekday [node]
  (parse-fixed-weekday node))


;; Relative to weekday in month

(defmethod -parse-holiday :tns:RelativeToWeekdayInMonth [node]
  (-> node
      (parse-attributes
       {:weekday ->keyword
        :when    ->keyword})
      (assoc :fixed-weekday (-> node (element :FixedWeekday) parse-fixed-weekday))))



;; Weekday relative to fixed day

(defmethod -parse-holiday :tns:FixedWeekdayRelativeToFixed [node]
  (-> node
      (parse-attributes
       {:which   ->keyword
        :weekday ->keyword
        :when    ->keyword})
      (assoc :date (-> node (element :day) parse-fixed))))


;; Christian

(defmethod -parse-holiday :tns:ChristianHoliday [node]
  (merge
   (parse-attributes
    node
    {:type       ->keyword
     :chronology ->keyword})
   (parse-moving-conditions node)))


(defmethod -parse-holiday :tns:RelativeToEasterSunday [node]
  {:chronology (-> node (element :chronology) :content first ->keyword)
   :days       (-> node (element :days) :content first ->int)})


;; Islamic

(defmethod -parse-holiday :tns:IslamicHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))


;; Hindu

(defmethod -parse-holiday :tns:HinduHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))


;; Hebrew

(defmethod -parse-holiday :tns:HebrewHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))


;; Ethiopian orthodox

(defmethod -parse-holiday :tns:EthiopianOrthodoxHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))


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
