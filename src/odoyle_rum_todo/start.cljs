(ns odoyle-rum-todo.start
  (:require [cljs.tools.reader :as r]
            [odoyle-rum-todo.core :as c]
            [rum.core :as rum])
  (:import goog.net.XhrIo))

(defn read-string [s]
  (binding [r/*suppress-read* true]
    (r/read-string {:read-cond :preserve :eof nil} s)))

;; read from initial state and insert any existing todos
(->> (.querySelector js/document "#initial-state")
     .-textContent
     js/atob
     read-string
     :all-todos
     (swap! c/*session c/insert-all-todos))

;; whenever the session is changed, send all todos to the server
(add-watch c/*session :save-all-todos
           (fn [_ _ _ session]
             (let [all-todos (c/get-all-todos session)]
               (.send XhrIo "/all-todos"
                      (fn [e]
                        (let [target (.-target e)
                              text (.getResponseText target)]
                          (when-not (.isSuccess target)
                            (js/alert text))))
                      "POST"
                      (pr-str all-todos)))))

;; mount the root component
(rum/hydrate (c/app-root c/*session)
  (.querySelector js/document "#app"))
