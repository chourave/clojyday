;; Copyright and license information at end of file

(ns clojyday.place

  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojyday.util :refer [$ string-array?]])

  (:import
   (de.jollyday.caching HolidayManagerValueHandler)
   (de.jollyday HolidayCalendar HolidayManager ManagerParameter ManagerParameters)
   (de.jollyday.configuration ConfigurationProviderManager)
   (de.jollyday.util Cache)
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


(defn read-manager-impl-class-name
  "Reads the managers implementation class from the properties config file"
  [parameter]
  (or (.getManagerImplClassName parameter)
      (throw (IllegalStateException.
              (str "Missing configuration '"
                   ManagerParameter/MANAGER_IMPL_CLASS_PREFIX
                   "'. Cannot create manager.")))))


(defn create-manager
  "Creates a new HolidayManager instance for the country
  and puts it to the manager cache"
  [parameter]
  (.mergeConfigurationProperties configuration-provider-manager parameter)
  (->> (read-manager-impl-class-name parameter)
       (HolidayManagerValueHandler. parameter)
       (.get holiday-manager-cache)))


;; Parsing a place

(defn holiday-manager
  "A holiday manager for a given locale or calendar id"
  ([calendar]
   (-> (holiday-calendars calendar)
       (or calendar)
       (ManagerParameters/create)
       create-manager)))

(s/fdef holiday-manager
  :args (s/cat :calendar `calendar-or-id)
  :ret  ::manager)


(defn parse-place
  "Splits a place specification into a calendar and sub-zones"
  [place]
  (let [[calendar & zones]
        (if (seqable? place) place [place])]
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
