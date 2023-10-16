(ns bench
  (:require
   [virtuoso.core :as v]
   [clj-http.client :as client]
   [cheshire.core :as json]))

(def URL "https://google.com")

(defn download [url]
  (-> URL
      client/get
      :body))


(def URLS
  (vec (repeat 100 URL)))


#_
(time
 (count
  (map download URLS)))

#_
(time
 (count
  (pmap download URLS)))

#_
(time
 (count
  (v/pmap! download URLS)))



(defn task [_]
  (Thread/sleep 500)
  42)


#_
(time (count (pmap task (repeat 50 nil))))

#_
(time (count (v/pmap! task (repeat 50 nil))))

#_
(time (count (map task (repeat 50 nil))))
