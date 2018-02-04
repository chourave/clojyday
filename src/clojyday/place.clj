;; Copyright and license information at end of file

(ns clojyday.place

  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojyday.util :as util])

  (:import
    (clojure.lang Named)
    (de.jollyday HolidayCalendar HolidayManager ManagerParameter)
    (de.jollyday.configuration ConfigurationProviderManager)
    (de.jollyday.datasource ConfigurationDataSourceManager)
    (de.jollyday.parameter CalendarPartManagerParameter UrlManagerParameter)
    (de.jollyday.util Cache Cache$ValueHandler ClassLoadingUtil)
    (java.net URL)
    (java.util Locale)))


;;

(defonce ^{:private true, :doc ""} format-hierarchy
  (make-hierarchy))


(defn parameter?
  "Is the argument a Jollyday manager parameter?"
  [x]
  (instance? ManagerParameter x))

(s/fdef parameter?, :args any?, :ret boolean?)


(defn format?
  ""
  [f]
  (isa? format-hierarchy f :any-format))

(s/fdef format?
  :args (s/cat :f keyword?)
  :ret boolean?)


(defn get-format
  ""
  [p]
  (as-> p %
    (.getProperty % "clojyday.configuration-format")
    (string/split % #"/")
    (apply keyword %)))

(s/fdef get-format
  :args (s/cat :p parameter?)
  :ret format?)


(defmulti configuration-data-source
  ""
  get-format
  :hierarchy #'format-hierarchy)


(defmulti -create-manager-parameters
  ""
 #(vector (type %1) %2)
 :hierarchy #'format-hierarchy)


;; Basic type predicates

(defn cache-value-handler?
  "Is the argument a Jollyday cache value handler?"
  [x]
  (instance? Cache$ValueHandler x))

(s/fdef cache-value-handler?, :args any?, :ret boolean?)


(defn locale?
  "Is the argument a Java Locale?"
  [x]
  (instance? Locale x))

(s/fdef locale?, :args any?, :ret boolean?)


;; Basic field types

(s/def ::zones util/string-array?)

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

(defn nil-safe-instance?
  [c x]
  (if (nil? c)
    (nil? x)
    (instance? c x)))

(s/def calendar-or-id
  (s/or :predefined holiday-calendars
        :open   (fn [c]
                  (->> -create-manager-parameters
                       methods
                       keys
                       (map first)
                       (some #(nil-safe-instance? % c))))))


(s/def calendar-and-zones
  (s/or :bare-calendar      `calendar-or-id
        :calendar-with-zone (s/cat :calendar `calendar-or-id
                                   :zone (s/* keyword?))))


(s/def manager-class-name
  (s/and string?
         #(.isAssignableFrom HolidayManager (Class/forName %))))


;; Jollyday HolidayManager instance creation

(def holiday-manager-cache
  "Cache for manager instances on a per country basis"
  (Cache.))


(def configuration-provider-manager
  "Manager for configuration providers, delivers the jollyday configuration"
  (ConfigurationProviderManager.))


(defn read-manager-impl-class-name
  "Reads the managers implementation class from the properties config file"
  [parameter]
  (or (.getManagerImplClassName parameter)
      (throw (IllegalStateException.
              (str "Missing configuration '"
                   ManagerParameter/MANAGER_IMPL_CLASS_PREFIX
                   "'. Cannot create manager.")))))


(s/fdef read-manager-impl-class-name
  :args (s/cat :parameter parameter?)
  :ret string?)


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


(s/fdef instantiate-manager
  :args (s/cat :manager-class-name `manager-class-name)
  :ret #(instance? HolidayManager %)
  :fn #(= (-> % :args :manager-class-name)
          (-> % :ret type .getName)))


(defn add-format
  ""
  ([format]
   (add-format format :any-format))
  ([format parent]
   (alter-var-root #'format-hierarchy derive format parent)
   nil))

(s/fdef add-format
  :args (s/cat :format keyword?
               :parent (s/? keyword?))
  :ret nil?)


(add-format :xml)
(add-format :xml-jaxb :xml)


(defn set-format!
  ""
  [p format]
  (->> format
       ((juxt namespace name))
       (remove empty?)
       (string/join "/")
       (.setProperty p "clojyday.configuration-format")))

(s/fdef set-format!
  :args (s/cat :p parameter?
               :format format?)
  :ret nil?)


(defmethod configuration-data-source :xml-jaxb
  [parameters]
  (-> (ConfigurationDataSourceManager.)
      (.getConfigurationDataSource parameters)))


(defn holiday-manager-value-handler
  "Creates the ValueHandler which constructs a HolidayManager"
  [parameter manager-class-name]
  (reify Cache$ValueHandler
    (getKey [_]
      (.createCacheKey parameter))
    (createValue [_]
      (doto (instantiate-manager manager-class-name)
        (.setConfigurationDataSource (configuration-data-source parameter))
        (.init parameter)))))

(s/fdef holiday-manager-value-handler
  :args (s/cat :parameter parameter?, :manager-class-name `manager-class-name)
  :ret cache-value-handler?)


(defn create-manager
  "Creates a new HolidayManager instance for the country
  and puts it to the manager cache"
  [parameter]
  (.mergeConfigurationProperties configuration-provider-manager parameter)
  (->> (read-manager-impl-class-name parameter)
       (holiday-manager-value-handler parameter)
       (.get holiday-manager-cache)))

(s/fdef create-manager
  :args (s/cat :parameter parameter?)
  :ret ::manager)


(defn normalized-calendar-part
  [calendar-part]
  (-> (if (string/blank? calendar-part)
        (-> (Locale/getDefault) (.getCountry))
        (string/trim calendar-part))
      string/lower-case))

(s/fdef normalized-calendar-part
  :args (s/cat :calendar-part (s/nilable string?))
  :ret (s/and
        string?
        util/lowercase?))


(defmethod -create-manager-parameters [String :xml]
  [calendar-part _]
  (-> calendar-part
      normalized-calendar-part
      (CalendarPartManagerParameter. nil)))

(defmethod -create-manager-parameters [Named :any-format]
  [calendar-part format]
  (-create-manager-parameters (name calendar-part) format))

(defmethod -create-manager-parameters [nil :any-format]
  [_ format]
  (-create-manager-parameters "" format))

(defmethod -create-manager-parameters [Locale :any-format]
  [lc format]
  (-create-manager-parameters (.getCountry lc) format))

(defmethod -create-manager-parameters [HolidayCalendar :any-format]
  [calendar format]
  (-create-manager-parameters (.getId calendar) format))

(defmethod -create-manager-parameters [URL :any-format]
  [calendar-file-url _]
  (UrlManagerParameter. calendar-file-url nil))


(defn create-manager-parameters
  ""
  [calendar format]
  (doto (-create-manager-parameters calendar format)
    (set-format! format)))

(s/fdef create-manager-parameters
  :args (s/cat :calendar any?, :format format?)
  :ret parameter?)


;; Parsing a place

(defn holiday-manager
  "A holiday manager for a given locale or calendar id"
  (^HolidayManager [config-format calendar]
   (-> (get holiday-calendars calendar calendar)
       (create-manager-parameters config-format)
       create-manager)))

(s/fdef holiday-manager
  :args (s/cat :config-format format?
               :calendar `calendar-or-id)
  :ret  ::manager)


(defn parse-place
  "Splits a place specification into a calendar and sub-zones"
  [config-format place]
  (let [[calendar & zones]
        (if (sequential? place) place [place])]
    {::zones   (into-array String (map name zones))
     ::manager (holiday-manager config-format calendar)}))

(s/fdef parse-place
  :args (s/cat :config-format format?
               :place `calendar-and-zones)
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
