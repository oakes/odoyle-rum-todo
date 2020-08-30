(ns odoyle-rum-todo.start-dev
  (:require [clojure.spec.test.alpha :as st]
            [odoyle-rum-todo.start :as start]
            [ring.middleware.file :refer [wrap-file]]
            [clojure.java.io :as io]))

(defn -main []
  (st/instrument)
  (.mkdirs (io/file "target" "public"))
  (-> start/handler
      (wrap-file "target/public")
      start/run-server))
