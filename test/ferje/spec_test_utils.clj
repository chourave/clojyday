;; Copyright and license information at end of file

(ns ferje.spec-test-utils
  (:require
   [clojure.spec.alpha :as s]
   [expound.alpha :as expound]
   [orchestra.spec.test :refer [instrument unstrument]]))

(defn instrument-fixture
  ""
  [f]
  (instrument)
  (binding [s/*explain-out* expound/printer]
    (try
      (f)
      (finally
        (unstrument)))))

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
