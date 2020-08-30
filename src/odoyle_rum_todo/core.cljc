(ns odoyle-rum-todo.core
  (:require [rum.core :as rum]
            [odoyle.rules :as o]
            [odoyle.rum :as orum]))

(def components
  (orum/ruleset
    {app-root
     [:what
      [::global ::text text]
      [::global ::size size]
      :then
      (let [*session (orum/prop)]
        [:div
         [:button {:on-click (fn [_]
                               (swap! *session
                                      (fn [session]
                                        (-> session
                                            (o/insert ::global ::size (inc size))
                                            o/fire-rules))))}
          "Enlarge"]
         [:p {:style {:font-size size}} text]])]}))

(def *session
  (-> (reduce o/add-rule (o/->session) components)
      (o/insert ::global {::text ""
                          ::size 20})
      atom))

(defn update-session [session id state]
  (o/fire-rules (o/insert session id state)))

