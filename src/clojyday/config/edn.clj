;; Copyright and license information at end of file

(ns clojyday.config.edn
  "Load configuration from edn files,
  and convert xml configuration files to edn configuration files."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.walk :refer [postwalk]]
   [clojyday.config.core :as config]
   [clojyday.config.xml :as xml-config]
   [clojyday.place :as place])

  (:import
   (de.jollyday.datasource ConfigurationDataSource)
   (de.jollyday.parameter BaseManagerParameter CalendarPartManagerParameter)
   (java.io PushbackReader)
   (de.jollyday.util ResourceUtil)))


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


(defn cal-edn-path
  "Path for the edn configuration file for a given `calendar-name`"
  [calendar-name]
  (io/file "holidays" (str (name calendar-name) "-holidays.edn")))

(s/fdef cal-edn-path
  :args (s/cat :cal ::config/calendar-name)
  :ret #(instance? java.io.File %))


(place/add-format :edn)


(defprotocol EdnSource
  ""
  (get-edn [_] ""))


(defn manager-parameter->edn
  ""
  [parameter]
  (if (satisfies? EdnSource parameter)
    (get-edn parameter)
    (-> parameter
        .createResourceUrl
        io/reader
        PushbackReader.
        edn/read)))


(defmethod place/configuration-data-source :edn
  [_]
  (reify
    ConfigurationDataSource
    (getConfiguration [_ parameters]
      (-> parameters manager-parameter->edn config/->Configuration))))


(defmethod place/-create-manager-parameters [String :edn]
  [calendar-part _]
  (proxy [CalendarPartManagerParameter] [(place/normalized-calendar-part calendar-part) nil]
    (createResourceUrl []
      (->> calendar-part
           cal-edn-path
           str
          (.getResource (ResourceUtil.))))))


(defmethod place/-create-manager-parameters [clojure.lang.IPersistentMap :edn]
  [config _]
  (proxy [BaseManagerParameter clojyday.config.edn.EdnSource] [nil]
    (createCacheKey []
      (-> config hash str))
    (get_edn []
      config)))


(defn sorted-configuration
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and parse it to an edn configuration, sorting
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


(defn fast-print
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and print is as edn to the `writer`, with emphasis
  on the speed of the conversion."
  [calendar-name writer]
  (binding [*out* writer]
    (prn (xml-config/read-configuration calendar-name))))

(s/fdef fast-print
  :args (s/cat :calendar-name ::config/calendar-name
               :writer #(instance? java.io.Writer %))
  :ret nil?)


(defn pretty-print
  "Read the configuration for `calendar-name` from an xml file from the
  Jollyday distribution, and print is as edn to the `writer`, with emphasis
  on a nice-looking output."
  [calendar-name writer]
  (pprint (sorted-configuration calendar-name) writer))

(s/fdef pretty-print
  :args (s/cat :calendar-name ::config/calendar-name
               :writer #(instance? java.io.Writer %))
  :ret nil?)


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
  :args (s/cat :target-dir string?
               :print fn?
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
