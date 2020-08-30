(ns odoyle-rum-todo.core
  (:require [rum.core :as rum]
            [odoyle.rules :as o]
            [odoyle.rum :as orum]))

(def rules
  (o/ruleset
    {::todo-item
     [:what
      [id ::text text]
      [id ::done done]
      :then
      (->> (o/query-all o/*session* ::todo-item)
           (sort-by :id)
           vec
           (o/insert o/*session* ::global ::all-todos)
           o/reset!)]}))

(def components
  (orum/ruleset
    {app-root
     [:what
      [::global ::all-todos all-todos]
      :then
      (let [*session (orum/prop)
            *local (orum/atom {:text "" :next-id 0})
            {:keys [text next-id]} @*local]
        [:div
         [:input {:value text
                  :on-change (fn [e]
                               (swap! *local assoc :text (-> e .-target .-value)))
                  :on-key-down (fn [e]
                                 (when (= 13 (.-keyCode e))
                                   (swap! *session
                                          (fn [session]
                                            (-> session
                                                (o/insert next-id {::text text ::done false})
                                                o/fire-rules)))
                                   (reset! *local {:text "" :next-id (inc next-id)})))}]
         (pr-str all-todos)])]}))

(def *session
  (-> (reduce o/add-rule (o/->session) (concat rules components))
      (o/insert ::global ::all-todos [])
      o/fire-rules
      atom))

(defn update-session [session id state]
  (o/fire-rules (o/insert session id state)))

