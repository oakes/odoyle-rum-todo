(ns odoyle-rum-todo.start
  (:require [cljs.tools.reader :as r]
            [odoyle-rum-todo.core :as c]
            [rum.core :as rum]))

(defn read-string [s]
  (binding [r/*suppress-read* true]
    (r/read-string {:read-cond :preserve :eof nil} s)))

(defn init []
  (->> (.querySelector js/document "#initial-state")
       .-textContent
       js/atob
       read-string
       (swap! c/*session c/update-session ::c/global))
  (rum/mount (c/app-root c/*session)
    (.querySelector js/document "#app")))

(init)
