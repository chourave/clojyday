;; Copyright and license information at end of file

(ns clojyday.place

  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojyday.util :refer [$ string-array?]])

  (:import
    (clojure.lang Named)
   (de.jollyday HolidayCalendar HolidayManager ManagerParameter ManagerParameters)
   (de.jollyday.caching HolidayManagerValueHandler)
    (de.jollyday.configuration ConfigurationProviderManager)
    (de.jollyday.datasource ConfigurationDataSourceManager)
    (de.jollyday.parameter CalendarPartManagerParameter UrlManagerParameter)
    (de.jollyday.util Cache Cache$ValueHandler ClassLoadingUtil)
    (java.net URL)
    (java.util Locale)))


;; Basic type predicates

(defn locale?
  "Is the argument a Java Locale?"
  [x]
  (instance? Locale x))

(s/fdef locale?, :args any?, :ret boolean?)


;; Basic field types

(s/def ::zones string-array?)

(s/def ::manager #(instance? HolidayManager %))


;; Reference data

(def holiday-calendars
  "{:country HolidayCalendar}"
  (into {}
        (map (fn [calendar]
               [(-> (.getId calendar)
                    string/lower-case
                    keyword)
                calendar]))
        (HolidayCalendar/values)))


;; Basic specs

(s/def calendar-or-id
  (s/or :part-kw  holiday-calendars
        :part-str string?
        :locale   locale?
        :calendar #(instance? HolidayCalendar %)
        :file-url #(instance? URL %)))

(s/def calendar-and-zones
  (s/or :bare-calendar      `calendar-or-id
        :calendar-with-zone (s/cat :calendar `calendar-or-id
                                   :zone (s/* keyword?))))


;; Jollyday HolidayManager instance creation

(def holiday-manager-cache
  "Cache for manager instances on a per country basis"
  (Cache.))


(def configuration-provider-manager
  "Manager for configuration providers, delivers the jollyday configuration"
  (ConfigurationProviderManager.))


(defprotocol ConfigurationFormat
  (configuration-data-source
    [this parameters]
    ""))


(def jollyday-configuration-format
  (reify ConfigurationFormat
    (configuration-data-source [_ parameters]
      (-> (ConfigurationDataSourceManager.)
          (.getConfigurationDataSource parameters)))))


(defn read-manager-impl-class-name
  "Reads the managers implementation class from the properties config file"
  [parameter]
  (or (.getManagerImplClassName parameter)
      (throw (IllegalStateException.
              (str "Missing configuration '"
                   ManagerParameter/MANAGER_IMPL_CLASS_PREFIX
                   "'. Cannot create manager.")))))


(defn instantiate-manager
  "Instantiates the manager implementing class"
  [manager-class-name]
  (try
    (->> manager-class-name
         (.loadClass (ClassLoadingUtil.))
         (.newInstance))
    (catch Exception e
      (throw (IllegalStateException.
              (str "Cannot create manager class " manager-class-name)
              e)))))


(defn holiday-manager-value-handler
  "Creates the ValueHandler which constructs a HolidayManager"
  [parameter config-format manager-class-name]
  (reify Cache$ValueHandler
    (getKey [_]
      (.createCacheKey parameter))
    (createValue [_]
      (doto (instantiate-manager manager-class-name)
        (.setConfigurationDataSource (configuration-data-source config-format parameter))
        (.init parameter)))))


(defn create-manager
  "Creates a new HolidayManager instance for the country
  and puts it to the manager cache"
  [parameter config-format]
  (.mergeConfigurationProperties configuration-provider-manager parameter)
  (->> (read-manager-impl-class-name parameter)
       (holiday-manager-value-handler parameter config-format)
       (.get holiday-manager-cache)))


(defprotocol ManagerParameterSource
  (create-manager-parameters
    [source]
    ""))


(extend-protocol ManagerParameterSource
  String
  (create-manager-parameters [calendar-part]
    (-> (if (string/blank? calendar-part)
          (-> (Locale/getDefault) (.getCountry))
          (string/trim calendar-part))
        string/lower-case
        (CalendarPartManagerParameter. nil)))

  Named
  (create-manager-parameters [calendar-part]
    (create-manager-parameters (name calendar-part)))

  nil
  (create-manager-parameters [_]
    (create-manager-parameters ""))

  Locale
  (create-manager-parameters [lc]
    (create-manager-parameters (.getCountry lc)))

  HolidayCalendar
  (create-manager-parameters [calendar]
    (create-manager-parameters (.getId calendar)))

  URL
  (create-manager-parameters [calendar-file-url]
    (UrlManagerParameter. calendar-file-url nil)))


;; Parsing a place

(defn holiday-manager
  "A holiday manager for a given locale or calendar id"
  ([calendar]
   (holiday-manager calendar jollyday-configuration-format))
  ([calendar config-format]
   (-> (if (satisfies? ManagerParameterSource calendar)
         calendar
         (holiday-calendars calendar))
       create-manager-parameters
       (create-manager config-format))))

(s/fdef holiday-manager
  :args (s/cat :calendar `calendar-or-id :config-format (s/? #(satisfies? ConfigurationFormat %)))
  :ret  ::manager)


(defn parse-place
  "Splits a place specification into a calendar and sub-zones"
  [place]
  (let [[calendar & zones]
        (if (coll? place) place [place])]
    {::zones   (into-array String (map name zones))
     ::manager (holiday-manager calendar)}))

(s/fdef parse-place
  :args (s/cat :place `calendar-and-zones)
  :ret  (s/keys :req [::zones ::manager]))


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
