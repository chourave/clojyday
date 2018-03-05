;; Copyright and license information at end of file

(ns ferje.config.edn

  (:require
   [clojure.spec.alpha :as s]
   [ferje.config.core :as config]
   [ferje.config.raw :as raw]
   [ferje.place :as place])

  (:import
   (de.jollyday.datasource ConfigurationDataSource)
   (de.jollyday.parameter BaseManagerParameter CalendarPartManagerParameter)
   (ferje.config.raw ConfigSource)
   (de.jollyday.util ResourceUtil)))


(place/add-format :edn)


(defn tag
  ""
  [tag]
  (let [tag (name tag)]
    #{tag (keyword tag) (symbol tag)}))


(defn fluff
  ""
  [fluff]
  (s/? (tag fluff)))

(defn named-as
  ""
  [values]
  (let [values (into #{}
                     (mapcat tag)
                     values)]
    (s/conformer #(or (some-> % values name keyword)
                   ::s/invalid))))


(s/def ::substitution
  (s/cat
   :substitute (named-as config/weekdays)
   :fluff (fluff :with)
   :with (named-as config/withs)
   :weekday (named-as config/weekdays)))


(s/def ::moving-conditions
  (s/*
   (s/cat
    :tag (tag :substitute)
    :substitutions (s/+ ::substitution))))

(s/def ::month (named-as config/months))

(s/def ::weekday (named-as config/weekdays))

(s/def ::when (named-as config/whens))

(s/def ::which (named-as config/whichs))

(s/def ::every
  (s/alt :canonical (named-as config/everys)
         :piecemeal (s/cat :prefix (s/? (s/or :number #{2 4 5 6}
                                              :parity (named-as #{:odd :even})))
                           :suffix (named-as #{:year :years}))))

(s/def ::description-key
  (s/conformer #(if (config/named? %)
                  (-> % name keyword)
                  ::s/invalid)))

(s/def ::chronology (named-as config/chronologies))

(s/def ::christian-type (named-as config/christian-types))

(s/def ::islamic-type (named-as config/islamic-types))

(s/def ::hindu-type (named-as config/hindu-types))

(s/def ::hebrew-type (named-as config/hebrew-types))

(s/def ::ethiopian-orthodox-type (named-as config/ethiopian-orthodox-types))

(s/def ::fixed
  (s/cat :month             ::month
         :day               ::config/day
         :moving-conditions ::moving-conditions))


(s/def ::fixed-weekday
  (s/cat :which ::which
         :weekday ::weekday
         :fluff (fluff :of)
         :month ::month))


(s/def ::holiday
  (s/or :christian-holiday ::christian-type
        :islamic-holiday ::islamic-type
        :hindu-holiday ::hindu-type
        :hebrew-holiday ::hebrew-type
        :ethiopian-orthodox-holiday ::ethiopian-orthodox-type

        :composite
        (s/cat
         :definition (s/alt
                      :fixed
                      ::fixed

                      :relative-to-fixed
                      (s/cat :offset (s/alt :days (s/cat :days  ::config/days
                                                         :fluff (fluff :days))
                                            :weekday ::weekday)
                             :when ::when
                             :reference ::fixed)

                      :fixed-weekday-between-fixed
                      (s/cat :weekday ::weekday
                             :tag (tag :between)
                             :from ::fixed
                             :fluff (fluff :and)
                             :to ::fixed)

                      :fixed-weekday
                      ::fixed-weekday

                      :relative-to-weekday-in-month
                      (s/cat :weekday ::weekday
                             :when ::when
                             :reference ::fixed-weekday)

                      :fixed-weekday-relative-to-fixed
                      (s/cat :which ::which
                             :weekday ::weekday
                             :when ::when
                             :reference ::fixed)

                      :christian-holiday
                      (s/cat :chronology (s/? ::chronology)
                             :type ::christian-type
                             :moving-conditions ::moving-conditions)

                      :relative-to-easter-sunday
                      (s/cat :days int?
                             :fluff (fluff :days)
                             :when (s/? ::when)
                             :chronology (s/? ::chronology)
                             :tag (tag :easter))

                      :islamic-holiday
                      ::islamic-type

                      :hindu-holiday
                      ::hindu-type

                      :hebrew-holiday
                      ::hebrew-type

                      :ethiopian-orthodox-holiday
                      ::ethiopian-orthodox-type)

         :options (s/* (s/alt
                        :valid-from      (s/cat :tag (s/alt :one (tag :valid-from)
                                                            :two (s/cat :valid (tag :valid) :from (tag :from)))
                                                :value ::config/valid-from)
                        :valid-to        (s/cat :tag (s/alt :one (tag :valid-to)
                                                            :two (s/cat :valid (tag :valid) :to (tag :to)))
                                                :value ::config/valid-to)
                        :valid            (s/cat :tag (tag :valid)
                                                 :from-tag (tag :from)
                                                 :from ::config/valid-from
                                                 :to-tag (tag :to)
                                                 :to ::config/valid-to)
                        :every           (s/cat :tag (tag :every)
                                                :value ::every)
                        :description-key (s/cat :tag (tag :description-key)
                                                :value ::description-key)
                        :localized-type  (named-as #{:official :unofficial :inofficial}))))))


(s/def ::holidays (s/coll-of ::holiday))

(s/def ::sub-configurations (s/coll-of ::configuration))

(defn keywordize-keys
  [m]
  (into {} (map (fn [[k v]] [(-> k name keyword) v])) m))

(s/def ::hierarchy
  (s/and config/named?
         (s/conformer #(-> % name keyword))))

(s/def ::configuration
  (s/and
   (s/map-of config/named? any?)
   (s/conformer keywordize-keys)
   (s/keys :req-un [::config/description ::hierarchy ::holidays]
           :opt-un [::sub-configurations])))

(defn holiday-type
  ""
  [conformed-holiday]
  (cond
    (not (map-entry? conformed-holiday))
    nil

    (= :composite (key conformed-holiday))
    (-> conformed-holiday val :definition key)

    :else
    (key conformed-holiday)))


(defmulti -edn->holiday
  ""
  holiday-type)

(defmulti parse-option
  ""
  key)

(defmethod parse-option :valid-from
  [[k v]]
  {k (:value v)})

(defmethod parse-option :valid-to
  [[k v]]
  {k (:value v)})

(defmethod parse-option :valid
  [[_ {:keys [from to]}]]
  {:valid-from from, :valid-to to})

(defmethod parse-option :every
  [[k {:keys [value]}]]
  {k (case (key value)
       :canonical (val value)
       :piecemeal ({nil :every-year
                    2 :2-years
                    4 :4-years
                    5 :5-years
                    6 :6-years
                    :odd :odd-years
                    :even :even-years} (some-> value val :prefix val)))})

(defmethod parse-option :description-key
  [[k v]]
  {k (:value v)})

(defmethod parse-option :localized-type
  [[k v]]
  {k ({:inofficial :unofficial-holiday
       :unofficial :unofficial-holiday
       :official   :official-holiday} v)})

(defn parse-common-options
  ""
  [conformed-holiday]
  (into {}
        (mapcat parse-option)
        (-> conformed-holiday val :options)))


(defn edn->holiday
  ""
  [holiday]
  (let [conformed (s/conform ::holiday holiday)
        type (holiday-type conformed)]
    (-> conformed
        -edn->holiday
        (assoc :holiday type)
        (merge (parse-common-options conformed)))))

(s/fdef edn->holiday
  :args (s/cat :holiday ::holiday)
  :ret `config/holiday)

(defn edn->configuration
  ""
  [configuration]
  (let [configuration (keywordize-keys configuration)]
    (-> configuration
        (update :holidays #(map edn->holiday %))
        (update :hierarchy #(-> % name keyword))
        (into (for [[k v] (select-keys configuration [:sub-configurations])]
                [k (map edn->configuration v)])))))

(s/fdef edn->configuration
  :args (s/cat :configuration ::configuration)
  :ret ::config/configuration)


(defmethod place/configuration-data-source :edn
  [_]
  (reify
    ConfigurationDataSource
    (getConfiguration [_ parameters]
      (-> parameters
          raw/manager-parameter->config
          edn->configuration
          config/->Configuration))))


(defmethod place/-create-manager-parameters [String :edn]
  [calendar-part _]
  (proxy [CalendarPartManagerParameter] [(place/normalized-calendar-part calendar-part) nil]
    (createResourceUrl []
      (->> calendar-part
           raw/cal-edn-path
           str
           (.getResource (ResourceUtil.))))))


(defmethod place/-create-manager-parameters [clojure.lang.IPersistentMap :edn]
  [config _]
  (proxy [BaseManagerParameter ConfigSource] [nil]
    (createCacheKey []
      (-> config hash str))
    (get_config []
      config)))


(defmulti -holiday->edn
  ""
  :holiday)


(defmulti format-common-options
  ""
  (fn [style _] style))

(defmethod format-common-options :code
  [_ holiday]
  (mapcat
   (fn [[_ v :as x]] (when v x))
   (select-keys holiday [:valid-from :valid-to :every :description-key])))

(defmethod format-common-options :english
  [_ {:keys [valid-from valid-to every description-key]}]
  (concat
   (cond
     (and valid-from valid-to)
     [:valid :from valid-from :to valid-to]

     valid-from [:valid :from valid-from]
     valid-to [:valid :to valid-to])
   (when every [:every every])
   (when description-key [:description-key description-key])))

(defn format-official-marker
  ""
  [holiday]
  (when (= :unofficial-holiday (:localized-type holiday))
    [:unofficial]))

(defn simplify-edn
  ""
  [holiday]
  (if (next holiday) holiday (first holiday)))

(s/def ::edn-style #{:code :english})

(defmulti apply-style
  ""
  #(-> %2))

(defmethod apply-style :code [holiday _] holiday)

(defmethod apply-style :english
  [holiday _]
  (into
   []
   (map #(if (keyword? %) (-> % name symbol) %))
   holiday))

(defn holiday->edn
  ""
  [style holiday]
  (-> (-holiday->edn holiday)
      (into (format-common-options style holiday))
      (into (format-official-marker holiday))
      (apply-style style)
      simplify-edn))

(s/fdef holiday->edn
  :args (s/cat :style ::edn-style, :holiday `config/holiday)
  :ret ::holiday)

(defmulti format-configuration
  ""
  #(-> %2))

(defmethod format-configuration :code
  [configuration _]
  configuration)

(defmethod format-configuration :english
  [configuration _]
  (into {} (map (fn [[k v]] [(-> k name symbol) v])) configuration))

(defn format-keyword
  ""
  [style keyword]
  (case style
    :english (-> keyword name symbol)
    :code keyword))

(defn configuration->edn
  ""
  [style configuration]
  (-> configuration
      (update :holidays #(for [h %] (holiday->edn style h)))
      (update :hierarchy #(format-keyword style %))
      (into (for [[k v] (select-keys configuration [:sub-configurations])]
              [k (for [c v] (configuration->edn style c))]))
      (format-configuration style)))

(s/fdef configuration->edn
  :args (s/cat :style ::edn-style, :configuration ::config/configuration)
  :ret ::configuration)


(defn composite-definition
  ""
  [holiday]
  (some-> holiday val :definition val))


(defn parse-moving-conditions
  ""
  [holiday]
  (when-let [conditions (:moving-conditions holiday)]
    {:moving-conditions
     (into []
           (mapcat #(for [s (:substitutions %)]
                      (select-keys s [:substitute :with :weekday])))
           conditions)}))

(defn parse-fixed-edn
  ""
  [fixed]
  (merge
   (select-keys fixed [:month :day])
   (parse-moving-conditions fixed)))

(defmethod -edn->holiday :fixed
  [holiday]
  (parse-fixed-edn (composite-definition holiday)))

(defn format-moving-conditions
  ""
  [holiday]
  (when-let [moving-conditions (:moving-conditions holiday)]
    (into [:substitute]
          (mapcat (juxt :substitute (constantly :with) :with :weekday))
          moving-conditions)))

(defn format-fixed-edn
  ""
  [fixed]
  (let [{:keys [month day]} fixed]
    (into [month day]
          (format-moving-conditions fixed))))

(defmethod -holiday->edn :fixed
  [holiday]
  (format-fixed-edn holiday))


(defmethod -edn->holiday :relative-to-fixed
  [holiday]
  (let [{:keys [offset when reference]} (composite-definition holiday)
        offset-type                     (key offset)]
    {:when       when
     :date       (parse-fixed-edn reference)
     offset-type ((case offset-type
                    :days    :days
                    :weekday identity)
                  (val offset))}))

(defmethod -holiday->edn :relative-to-fixed
  [holiday]
  (let [{:keys [when days weekday date]} holiday
        offset                           (if days [days :days] [weekday])]
    (into offset (cons when (format-fixed-edn date)))))


(defmethod -edn->holiday :fixed-weekday-between-fixed
  [holiday]
  (let [{:keys [from to weekday]} (composite-definition holiday)]
    {:from    (parse-fixed-edn from)
     :to      (parse-fixed-edn to)
     :weekday weekday}))

(defmethod -holiday->edn :fixed-weekday-between-fixed
  [holiday]
  (let [{:keys [from to weekday]} holiday]
    (into [weekday :between]
          (concat
           (format-fixed-edn from)
           [:and]
           (format-fixed-edn to)))))


(defn parse-fixed-weekday-edn
  ""
  [fixed-weekday]
  (select-keys fixed-weekday [:month :which :weekday]))

(defmethod -edn->holiday :fixed-weekday
  [holiday]
  (parse-fixed-weekday-edn (composite-definition holiday)))

(defmethod -holiday->edn :fixed-weekday
  [holiday]
  (let [{:keys [month which weekday]} holiday]
    [which weekday :of month]))


(defmethod -edn->holiday :relative-to-weekday-in-month
  [holiday]
  (let [definition (composite-definition holiday)]
    (assoc (select-keys definition [:weekday :when])
           :fixed-weekday (parse-fixed-weekday-edn (:reference definition)))))

(defmethod -holiday->edn :relative-to-weekday-in-month
  [holiday]
  (let [{:keys [weekday when fixed-weekday]} holiday
        {:keys [month which] ref-weekday :weekday} fixed-weekday]
    [weekday when which ref-weekday :of month]))


(defmethod -edn->holiday :fixed-weekday-relative-to-fixed
  [holiday]
  (let [definition (composite-definition holiday)]
    (assoc (select-keys definition [:which :weekday :when])
           :date (parse-fixed-edn (:reference definition)))))

(defmethod -holiday->edn :fixed-weekday-relative-to-fixed
  [holiday]
  (let [{:keys [which weekday when date]} holiday]
    (into
     [which weekday when]
     (format-fixed-edn date))))


(defmethod -edn->holiday :christian-holiday
  [holiday]
  (if-let [definition (composite-definition holiday)]
    (let [{:keys [chronology]} definition]
      (cond-> (select-keys definition [:type])
        chronology (assoc :chronology chronology)
        :alwas (merge (parse-moving-conditions definition))))
    {:type (val holiday)}))

(defmethod -holiday->edn :christian-holiday
  [holiday]
  (let [{:keys [type chronology]} holiday]
    (into []
          (keep identity)
          (concat [chronology type]
                  (format-moving-conditions holiday)))))


(defmethod -edn->holiday :relative-to-easter-sunday
  [holiday]
  (let [{:keys [days when chronology] :or {when :after, chronology :gregorian}}
        (composite-definition holiday)

        sign ({:before -1, :after 1} when)]
    {:days       (* sign days)
     :chronology chronology}))

(defmethod -holiday->edn :relative-to-easter-sunday
  [holiday]
  (let [{:keys [days chronology]} holiday]
    (if (neg? days)
      [(- days) :days :before chronology :easter]
      [days :days :after chronology :easter])))


(defn parse-simple-type-edn
  ""
  [holiday]
  {:type (or (composite-definition holiday)
             (val holiday))})

(defmethod -edn->holiday :islamic-holiday
  [holiday]
  (parse-simple-type-edn holiday))

(defn format-simple-type-edn
  ""
  [holiday]
  [(:type holiday)])

(defmethod -holiday->edn :islamic-holiday
  [holiday]
  (format-simple-type-edn holiday))


(defmethod -edn->holiday :hindu-holiday
  [holiday]
  (parse-simple-type-edn holiday))

(defmethod -holiday->edn :hindu-holiday
  [holiday]
  (format-simple-type-edn holiday))


(defmethod -edn->holiday :hebrew-holiday
  [holiday]
  (parse-simple-type-edn holiday))

(defmethod -holiday->edn :hebrew-holiday
  [holiday]
  (format-simple-type-edn holiday))


(defmethod -edn->holiday :ethiopian-orthodox-holiday
  [holiday]
  (parse-simple-type-edn holiday))

(defmethod -holiday->edn :ethiopian-orthodox-holiday
  [holiday]
  (format-simple-type-edn holiday))


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
