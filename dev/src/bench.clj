(ns bench
  (:require
   [virt.core :as virt]
   [clj-http.client :as client]
   [cheshire.core :as json]))

(def URL "https://google.com")

(defn download [url]
  (-> URL
      client/get
      :body))


#_
(time
 (count
  (pmap download (repeat 200 URL))))

#_
(time
 (count
  (virt/pmap! download (repeat 200 URL))))

#_
(time
 (count
  (map download (repeat 200 URL))))
