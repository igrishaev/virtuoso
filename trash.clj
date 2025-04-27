
(time
 (do
   (mapv deref
         (map
          (fn [x]
            (future
              (cheshire/get "https://github.com/seductiveapps/largeJSON/raw/master/100mb.json")))
          (range 0 10)))
   :end))

;; "https://microsoftedge.github.io/Demos/json-dummy-data/1MB.json

;; "https://raw.githubusercontent.com/json-iterator/test-data/refs/heads/master/large-file.json"

(time (->> (clojure.core/range 0 100)
           (clojure.core/map
            (fn [x]
              (clojure.core/future
                (try
                  (clj-http.client/get "https://microsoftedge.github.io/Demos/json-dummy-data/1MB.json")
                  x
                  (catch Throwable e
                    :error)
                  (finally
                    (println x "DONE"))))))
           (clojure.core/doall)
           (clojure.core/mapv deref)))


(time (->> (clojure.core/range 0 100)
           (map
            (fn [x]
              (try
                (clj-http.client/get "https://microsoftedge.github.io/Demos/json-dummy-data/1MB.json")
                x
                (catch Throwable e
                  :error)
                (finally
                  (println x "DONE")))))
           (doall)))
