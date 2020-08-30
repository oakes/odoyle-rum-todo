(ns odoyle-rum-todo.core
  (:require [rum.core :as rum]
            [odoyle.rules :as o]
            [odoyle.rum :as orum]))

(defn refresh-all-todos [session]
  (->> (o/query-all session ::get-todo-item)
       (sort-by :id)
       vec
       (o/insert session ::global ::all-todos)))

(defn insert-todo! [*session id text]
  (swap! *session
         (fn [session]
           (-> session
               (o/insert id {::text text ::done false})
               o/fire-rules))))

(defn retract-todo! [*session id]
  (swap! *session
         (fn [session]
           (-> session
               (o/retract id ::text)
               (o/retract id ::done)
               refresh-all-todos
               o/fire-rules))))

(def rules
  (o/ruleset
    {::get-todo-item
     [:what
      [id ::text text]
      [id ::done done]
      :then
      (o/reset! (refresh-all-todos o/*session*))]}))

(def components
  (orum/ruleset
    {app-root
     [:then
      (let [*session (orum/prop)]
        [:section#todoapp
         (todo-input *session)
         (todo-list *session)])]
     
     todo-input
     [:then
      (let [*session (orum/prop)
            *local (orum/atom {:text "" :next-id 0})
            {:keys [text next-id]} @*local]
        [:input {:type "text"
                 :class "edit"
                 :autoFocus true
                 :value text
                 :on-change (fn [e]
                              (swap! *local assoc :text (-> e .-target .-value)))
                 :on-key-down (fn [e]
                                (when (= 13 (.-keyCode e))
                                  (insert-todo! *session next-id text)
                                  (reset! *local {:text "" :next-id (inc next-id)})))}])]
     
     todo-list
     [:what
      [::global ::all-todos all-todos]
      :then
      (let [*session (orum/prop)]
        [:section#main
          [:ul#todo-list
            (for [todo all-todos]
              ^{:key (:id todo)} (todo-item {:*session *session :todo todo}))]])]
     
     todo-item
     [:then
      (let [{:keys [*session todo]} (orum/prop)
            {:keys [id text done]} todo]
        [:li {:class (when done "completed")}
          [:div.view
            [:input.toggle
              {:type "checkbox"
               :checked done}]
            [:label text]
            [:button.destroy
              {:on-click #(retract-todo! *session id)}]]])]}))

(def *session
  (-> (reduce o/add-rule (o/->session) (concat rules components))
      (o/insert ::global ::all-todos [])
      o/fire-rules
      atom))

(defn update-session [session id state]
  (o/fire-rules (o/insert session id state)))

