(ns clojyday.localized
  (:require
   [clojure.spec.alpha :as s]))


(defprotocol Localized
  "Things that have a :description that can be localized."
  (-localize [_ resource-util locale]
    "Returns a version with a :description for the specified locale.
  Acts recursively when it makes sense."))


(defn localized?
  "Does x satisy the Localized protocol?"
  [x]
  (satisfies? Localized x))

(s/fdef localized?, :args any?, :ret boolean?)
