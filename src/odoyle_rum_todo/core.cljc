(ns odoyle-rum-todo.core
  (:require [rum.core :as rum]
            [odoyle.rules :as o]
            [odoyle.rum :as orum]))

(defn refresh-all-todos [session]
  (->> (o/query-all session ::get-todo-item)
       (sort-by :id)
       vec
       (o/insert session ::global ::all-todos)))

(defn insert-todo! [*session id attr->value]
  (swap! *session
         (fn [session]
           (-> session
               (o/insert id attr->value)
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
         [:header#header
          [:h1 "todos"]
          (todo-input {:initial-text ""
                       :*session *session})]
         (todo-list *session)])]
     
     todo-input
     [:then
      (let [{:keys [*session initial-text id on-save on-stop]} (orum/prop)
            *local (orum/atom {:text initial-text :next-id (or id 0)})
            {:keys [text next-id]} @*local
            on-save (or on-save #(reset! *local {:text "" :next-id (inc next-id)}))
            on-stop (or on-stop #(swap! *local assoc :text ""))]
        [:input {:type "text"
                 :class "edit"
                 :placeholder (if id
                                "Enter your edit"
                                "What needs to be done?")
                 :autoFocus true
                 :value text
                 :on-blur on-stop
                 :on-change (fn [e]
                              (swap! *local assoc :text (-> e .-target .-value)))
                 :on-key-down (fn [e]
                                (case (.-keyCode e)
                                  13
                                  (let [todo (if id
                                               {::text text}
                                               ;; if the todo is new, set ::done as well
                                               {::text text ::done false})]
                                    (insert-todo! *session next-id todo)
                                    (on-save))
                                  27
                                  (on-stop)
                                  ;; else
                                  nil))}])]
     
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
      (let [*editing (orum/atom false)
            {:keys [*session todo]} (orum/prop)
            {:keys [id text done]} todo]
        [:li {:class (str (when done "completed ")
                          (when @*editing "editing"))}
          [:div.view
            [:input.toggle
              {:type "checkbox"
               :checked done
               :on-change #(insert-todo! *session id {::done (not done)})}]
            [:label
             {:on-double-click #(reset! *editing true)}
             text]
            [:button.destroy
              {:on-click #(retract-todo! *session id)}]]
          (when @*editing
            (todo-input
              {:*session *session
               :initial-text text
               :id id
               :on-save #(reset! *editing false)
               :on-stop #(reset! *editing false)}))])]}))

(def *session
  (-> (reduce o/add-rule (o/->session) (concat rules components))
      (o/insert ::global ::all-todos [])
      o/fire-rules
      atom))

(defn update-session [session id state]
  (o/fire-rules (o/insert session id state)))

