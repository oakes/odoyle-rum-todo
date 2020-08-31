(ns odoyle-rum-todo.start
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.util.request :refer [body-string]]
            [ring.util.response :refer [not-found]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.data.codec.base64 :as base64]
            [rum.core :as rum]
            [odoyle.rum :as orum]
            [odoyle-rum-todo.core :as c])
  (:gen-class))

(def port 3000)

(defn page [initial-state]
  (binding [;; this binding causes the new matches triggered by `insert-all-todos`
            ;; to be stored locally, so they don't affect other users
            ;; that happen to be requesting this route at the same time
            orum/*matches* (volatile! {})]
    ;; if there are any todos in the user's ring session,
    ;; insert them into the o'doyle session.
    ;; we are only doing this for side-effects.
    (c/insert-all-todos @c/*session (:all-todos initial-state))
    ;; render the html
    (-> "template.html" io/resource slurp
        (str/replace "{{content}}" (rum/render-html (c/app-root nil)))
        ;; save the todos in a hidden div that the client can read when it loads
        ;; we are using base64 to prevent breakage (i.e. if a todo contains angle brackets)
        (str/replace "{{initial-state}}" (-> (pr-str initial-state)
                                             (.getBytes "UTF-8")
                                             base64/encode
                                             (String. "UTF-8"))))))

(defmulti handler (juxt :request-method :uri))

(defmethod handler [:get "/"]
  [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (page (:session request))})

(defmethod handler [:post "/all-todos"]
  [request]
  {:status 200
   :session (assoc (:session request) :all-todos
                   (edn/read-string (body-string request)))})

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

