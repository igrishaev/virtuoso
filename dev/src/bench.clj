(ns bench
  (:require
   [virtuoso.v3 :as v3]
   [clj-http.client :as client]
   [cheshire.core :as json]))

;; /opt/homebrew/var/www
(def URL "http://127.0.0.1:8080/hugefile.bin")

(defn download [i]
  (with-open [in ^java.io.InputStream
              (:body (client/get URL {:as :stream}))
              out
              (java.io.OutputStream/nullOutputStream)]
    (.transferTo in out)))


(def SEQ
  (vec (range 100)))


(comment

  "Elapsed time: 1102802.057709 msecs"
  (time
   (count
    (map download SEQ)))

  "Elapsed time: 44213.30375 msecs"
  (time
   (count
    (pmap download SEQ)))

  "Elapsed time: 11124.417959 msecs"
  (time
   (count
    (v3/map download SEQ)))

  "Elapsed time: 11090.514792 msecs"
  (time
   (count
    (v3/pmap 512 download SEQ)))

  )
