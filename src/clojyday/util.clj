;; Copyright and license information at end of file

(ns clojyday.util
  (:require
   [clojure.spec.alpha :as s]))


;; Basic type predicates

(def StringArray
  "Just the String[] class, for type comparisons"
  (class (make-array String 0)))


(defn string-array?
  "Is the argument a Java String array?"
  [x]
  (instance? StringArray x))

(s/fdef string-array?, :args any?, :ret boolean?)


;; Utility functions

(defn $
  "Plain old function application, handy with condp"
  [f x]
  (f x))

(s/fdef $
  :args (s/cat :f ifn? :x any?)
  :ret  any?
  :fn   #(= (:ret %)
            ((get-in % [:args :f]) (get-in % [:args :x]))))


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
