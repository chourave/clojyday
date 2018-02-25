(ns ferje.config.edn
  (:require
   [clojure.spec.alpha :as s]
   [ferje.config.core :as config]))

(s/def substitution
  (s/cat
   :substitute ::config/weekday
   :fluff (s/? #{:with})
   :with ::config/with
   :weekday ::config/weekday))


(s/def moving-conditions
  (s/*
   (s/cat
    :tag #{:substitute}
    :substitutions (s/* `substitution))))


(s/def fixed
  (s/cat :month             ::config/month
         :day               ::config/day
         :moving-conditions `moving-conditions))


(s/def fixed-weekday
  (s/cat :which ::config/which
         :weekday ::config/weekday
         :fluff (s/? #{:of})
         :month ::config/month))


(s/def holiday
  (s/or :christian-holiday :christian/type
        :islamic-holiday :islamic/type
        :hindu-holiday :hindu/type
        :hebrew-holiday :hebrew/type
        :ethiopian-orthodox-holiday :ethiopian-orthodox/type

        :composite
        (s/cat
         :definition (s/alt
                      :fixed
                      `fixed

                      :relative-to-fixed
                      (s/cat :offset (s/alt :days (s/cat :days  ::config/days
                                                         :fluff (s/? #{:days}))
                                            :weekday ::config/weekday)
                             :when ::config/when
                             :reference `fixed)

                      :fixed-weekday-between-fixed
                      (s/cat :weekday ::config/weekday
                             :tag #{:between}
                             :from `fixed
                             :fluff (s/? #{:and})
                             :to `fixed)

                      :fixed-weekday
                      `fixed-weekday

                      :relative-to-weekday-in-month
                      (s/cat :weekday ::config/weekday
                             :when ::config/when
                             :reference `fixed-weekday)

                      :fixed-weekday-relative-to-fixed
                      (s/cat :which ::config/which
                             :weekday ::config/weekday
                             :when ::config/when
                             :reference `fixed)

                      :christian-holiday
                      (s/cat :chronology (s/? ::config/chronology)
                             :type :christian/type
                             :moving-conditions `moving-conditions)

                      :relative-to-easter-sunday
                      (s/cat :days int?
                             :fluff (s/? #{:days})
                             :when (s/? ::config/when)
                             :chronology (s/? ::config/chronology)
                             :tag #{:easter})

                      :islamic-holiday
                      :islamic/type

                      :hindu-holiday
                      :hindu/type

                      :hebrew-holiday
                      :hebrew/type

                      :ethiopian-orthodox-holiday
                      :ethiopian-orthodox/type)

         :options (s/keys* :opt-un [::config/valid-from
                                    ::config/valid-to
                                    ::config/every
                                    ::config/description-key])

         :localized-type (s/? #{:official :unofficial :inofficial}))))


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

(defn parse-common-options
  ""
  [conformed-holiday]
  (select-keys (-> conformed-holiday val :options)
               [:valid-from :valid-to :every :description-key]))

(defn parse-official-marker
  ""
  [conformed-holiday]
  (when-let [localized-type
             (some-> conformed-holiday
                     val
                     :localized-type
                     {:inofficial :unofficial-holiday
                      :unofficial :unofficial-holiday
                      :official   :official-holiday})]
    {:localized-type localized-type}))

(defn edn->holiday
  ""
  [holiday]
  (let [conformed (s/conform `holiday holiday)]
    (-> conformed
        -edn->holiday
        (assoc :holiday (holiday-type conformed))
        (merge (parse-common-options conformed)
               (parse-official-marker conformed)))))

(s/fdef edn->holiday
  :args (s/cat :holiday `holiday)
  :ret `config/holiday)


(defmulti -holiday->edn
  ""
  :holiday)


(defn format-common-options
  ""
  [holiday]
  (mapcat
   (fn [[_ v :as x]] (when v x))
   (select-keys holiday [:valid-from :valid-to :every :description-key])))

(defn format-official-marker
  ""
  [holiday]
  (when (= :unofficial-holiday (:localized-type holiday))
    [:unofficial]))

(defn simplify-edn
  ""
  [holiday]
  (if (next holiday) holiday (first holiday)))

(defn holiday->edn
  ""
  [holiday]
  (-> holiday
      -holiday->edn
      (into (format-common-options holiday))
      (into (format-official-marker holiday))
      simplify-edn))

(s/fdef holiday->edn
  :args (s/cat :holiday `config/holiday)
  :ret `holiday)


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
           (mapcat (fn [{:keys [substitutions]}]
                     (map #(select-keys % [:substitute :with :weekday]) substitutions)))
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
