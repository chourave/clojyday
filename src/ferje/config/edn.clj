(ns ferje.config.edn
  (:require
   [clojure.spec.alpha :as s]
   [ferje.config.core :as config]))

(s/def substitution
  (s/cat
   :from ::config/weekday
   :fluff (s/? #{:with})
   :direction ::config/with
   :to ::config/weekday))
  

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
                      (s/cat :type :christian/type
                             :chronology (s/? ::config/chronology)
                             :moving-conditions `moving-conditions)

                      :relative-to-easter-sunday
                      (s/cat :days int?
                             :fluff (s/? #{:days})
                             :when (s/? ::config/when)
                             :tag #{:easter})

                      :islamic-holiday
                      :islamic/type

                      :hindu-holiday
                      :hindu/type

                      :hebrew-holiday
                      :hebrew/type

                      :ethiopian-orthodox-holiday
                      :ethiopian-orthodox/type)

         :options    (s/cat
                      :options (s/keys* :opt-un [::config/valid-from
                                                 ::config/valid-to
                                                 ::config/every
                                                 ::config/description-key])
                      :localized-type (s/? #{:official :unofficial :inofficial})))))
