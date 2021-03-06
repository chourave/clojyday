;; Copyright and license information at end of file

(ns ferje.config.raw
  "Load configuration from raw edn files,
  and convert xml configuration files to raw edn configuration files."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.walk :refer [postwalk]]
   [ferje.config.core :as config]
   [ferje.config.xml :as xml-config]
   [ferje.place :as place])

  (:import
   (de.jollyday.datasource ConfigurationDataSource)
   (de.jollyday.parameter BaseManagerParameter CalendarPartManagerParameter)
   (java.io PushbackReader)
   (de.jollyday.util ResourceUtil)))


(def key-order
  "Order in which keys should appear in the raw edn configuration (purely for readability)"
  [;; configuration
   :hierarchy, :description, :holidays, :sub-configurations

   ;; holiday prefix
   :holiday

   ;; weekday
   :which, :weekday, :from, :to

   ;; relative prefix
   :when, :date, :days

   ;; fixed
   :month, :day

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


(defn cal-edn-path
  "Path for the raw edn configuration file for a given `calendar-name`"
  [calendar-name]
  (io/file "holidays" (str (name calendar-name) "-holidays.edn")))

(s/fdef cal-edn-path
  :args (s/cat :cal ::config/calendar-name)
  :ret #(instance? java.io.File %))


(place/add-format :raw)


(defprotocol ConfigSource
  "Protocols for manager parameters that know how to directly
  return Ferje configuration maps"
  (get-config [parameters] "Return a configuration map corresponding to the parameters"))


(defn manager-parameter->config
  "Create a configuration map from manager parameters"
  [parameter]
  (if (satisfies? ConfigSource parameter)
    (get-config parameter)
    (-> parameter
        .createResourceUrl
        io/reader
        PushbackReader.
        edn/read)))


(defmethod place/configuration-data-source :raw
  [_]
  (reify
    ConfigurationDataSource
    (getConfiguration [_ parameters]
      (-> parameters manager-parameter->config config/->Configuration))))


(defmethod place/-create-manager-parameters [String :raw]
  [calendar-part _]
  (proxy [CalendarPartManagerParameter] [(place/normalized-calendar-part calendar-part) nil]
    (createResourceUrl []
      (->> calendar-part
           cal-edn-path
           str
          (.getResource (ResourceUtil.))))))


(defmethod place/-create-manager-parameters [clojure.lang.IPersistentMap :raw]
  [config _]
  (proxy [BaseManagerParameter ferje.config.raw.ConfigSource] [nil]
    (createCacheKey []
      (-> config hash str))
    (get_config []
      config)))


(defn sorted-configuration
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and parse it to a raw edn configuration, sorting
  keys to make it easier to read for humans.

  Example: (sorted-configuration :fr)"
  [calendar-name]
  (try
    (->> calendar-name
         xml-config/read-configuration
         (postwalk #(if (map? %) (sort-map %) %)))
    (catch Exception e
      (throw (Exception. (str "While reading calendar " (name calendar-name))
                         e)))))

(s/fdef sorted-configuration
  :args (s/cat :calendar-name ::config/calendar-name)
  :ret ::config/configuration)


(defn fast-convert
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and print is a raw edn to the `writer`, with emphasis
  on the speed of the conversion."
  [calendar-name writer]
  (binding [*out* writer]
    (prn (xml-config/read-configuration calendar-name))))

(s/fdef fast-convert
  :args (s/cat :calendar-name ::config/calendar-name
               :writer #(instance? java.io.Writer %))
  :ret nil?)


(defn pretty-convert
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and print is as raw edn to the `writer`, with emphasis
  on a nice-looking output."
  [calendar-name writer]
  (binding [pprint/*print-right-margin* 110]
    (pprint (sorted-configuration calendar-name) writer)))

(s/fdef pretty-convert
  :args (s/cat :calendar-name ::config/calendar-name
               :writer #(instance? java.io.Writer %))
  :ret nil?)


(defn xml->raw
  "Convert the calendar named `calendar-name` from an xml file in the Jollyday
  distribution to a raw edn file in `target`. `convert` should be either
  `pretty-convert` or `fast-convert`."
  [target-dir convert calendar-name]
  (let [f (io/file target-dir (cal-edn-path calendar-name))]
    (io/make-parents f)
    (convert calendar-name (io/writer f))))

(s/fdef xml->raw
  :args (s/cat :target-dir string?
               :convert fn?
               :calendar-name ::config/calendar-name)
  :ret nil?)


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
