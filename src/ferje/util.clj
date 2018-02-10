;; Copyright and license information at end of file

(ns ferje.util
  "String manipulation"
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]))


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


;; String manipulation

(defn equals-ignore-case?
  "Case insensitive string comparison"
  [s1 s2]
  (= (-> s1 string/lower-case)
     (-> s2 string/lower-case)))

(s/fdef equals-ignore-case?
  :args (s/cat :s1 string? :s2 string?)
  :ret boolean?)


(defn lowercase?
  "Does `s` contain no upper case characters?"
  [s]
  (= s (string/lower-case s)))

(s/fdef lowercase?
  :args (s/cat :s string?)
  :ret boolean?)


(defn uppercase?
  "Does `s` contain no lower case characters?"
  [s]
  (= s (string/upper-case s)))

(s/fdef uppercase?
  :args (s/cat :s string?)
  :ret boolean?)


(defn strip
  "Remvoe any occurrences of `to-strip` in `s`"
  [s to-strip]
  (string/replace s to-strip ""))

(s/fdef strip
  :args (s/cat :s string? :to-strip string?)
  :ret string?)


(defn kebab->camel
  "Turn kebab-case strings into camelCase"
  [s]
  (let [[head & tail] (string/split s #"-")]
    (apply str
           head
           (map string/capitalize tail))))

(s/fdef kebab->camel
  :args (s/cat :s string?)
  :ret string?
  :fn #(equals-ignore-case?
        (:ret %)
        (-> % :args :s (strip "-"))))


(defn camel->kebab
  "Turn camelCase (and PascalCase) strings into kebab-case"
  [s]
  (as-> s %
    (string/split % #"(?=[A-Z])")
    (string/join \- %)
    (string/lower-case %)))

(s/fdef camel->kebab
  :args (s/cat :s string?)
  :ret string?
  :fn (s/and
       #(-> % :ret lowercase?)
       #(equals-ignore-case?
         (-> % :ret (strip "-"))
         (-> % :args :s))))


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
