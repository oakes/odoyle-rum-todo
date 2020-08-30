(ns odoyle-rum-todo.start
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.util.response :refer [not-found]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.codec.base64 :as base64]
            [rum.core :as rum]
            [odoyle-rum-todo.core :as c])
  (:gen-class))

(def port 3000)

(defn page []
  (let [state {::c/text "Hello, world!"}
        session (c/update-session @c/*session ::c/global state)]
    (-> "template.html" io/resource slurp
        (str/replace "{{content}}" (rum/render-html (c/app-root (atom session))))
        (str/replace "{{initial-state}}" (-> (pr-str state)
                                             (.getBytes "UTF-8")
                                             base64/encode
                                             (String. "UTF-8"))))))

(defmulti handler (juxt :request-method :uri))

(defmethod handler [:get "/"]
  [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (page)})

(defmethod handler :default
  [request]
  (not-found "Page not found"))

(defn run-server [handler-fn]
  (run-jetty (-> handler-fn
                 (wrap-resource "public")
                 wrap-session
                 wrap-content-type
                 wrap-gzip)
             {:port port :join? false})
  (println (str "Started server on http://localhost:" port)))

(defn -main [& args]
  (run-server handler))

