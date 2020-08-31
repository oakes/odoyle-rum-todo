(ns odoyle-rum-todo.core
  (:require [rum.core :as rum]
            [odoyle.rules :as o]
            [odoyle.rum :as orum])
  #?(:cljs (:require-macros [odoyle.rules]
                            [odoyle.rum])))

(defn refresh-all-todos [session]
  (->> (o/query-all session ::get-todo-item)
       (sort-by :id)
       vec
       (o/insert session ::global ::all-todos)))

(defn insert-all-todos [session todos]
  (->> todos
       (reduce
         (fn [session {:keys [id text done]}]
           (o/insert session id {::text text ::done done}))
         session)
       o/fire-rules))

(defn get-all-todos [session]
  (-> (o/query-all session ::get-all-todos)
      first
      :all-todos))

(defn insert! [*session id attr->value]
  (swap! *session
         (fn [session]
           (-> session
               (o/insert id attr->value)
               o/fire-rules))))

(defn retract! [*session id]
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
      (o/reset! (refresh-all-todos o/*session*))]
     
     ::get-all-todos
     [:what
      [::global ::all-todos all-todos]]}))

(def components
  (orum/ruleset
    {app-root
     [:then
      (let [*session (orum/prop)]
        [:div
         [:section#todoapp
          [:header#header
           [:h1 "todos"]
           (todo-input {:initial-text ""
                        :*session *session})]
          (todo-list *session)
          (footer *session)]
         [:footer#info
          [:p "Double-click to edit a todo"]]])]

     footer
     [:what
      [::global ::all-todos all-todos]
      [::global ::showing showing]
      :then
      (let [*session (orum/prop)
            active-todos (remove :done all-todos)
            completed-todos (filter :done all-todos)
            active (count active-todos)
            completed (count completed-todos)
            filter-attrs (fn [filter-kw]
                           {:class (when (= filter-kw showing) "selected")
                            :on-click #(insert! *session ::global {::showing filter-kw})})]
        [:footer#footer
         [:span#todo-count
          [:strong active] " " (case active 1 "item" "items") " left"]
         [:ul#filters
          [:li [:a (filter-attrs :all) "All"]]
          [:li [:a (filter-attrs :active) "Active"]]
          [:li [:a (filter-attrs :completed) "Completed"]]]
         (when (pos? completed)
           [:button#clear-completed {:on-click #(run! (partial retract! *session)
                                                      (map :id completed-todos))}
            "Clear completed"])])]
     
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
                                    (insert! *session next-id todo)
                                    (on-save))
                                  27
                                  (on-stop)
                                  ;; else
                                  nil))}])]
     
     todo-list
     [:what
      [::global ::all-todos all-todos]
      [::global ::showing showing]
      :then
      (let [*session (orum/prop)]
        [:section#main
          [:ul#todo-list
            (for [todo all-todos
                  :when (case showing
                          :all true
                          :active (not (:done todo))
                          :completed (:done todo))]
              (-> (todo-item {:*session *session :todo todo})
                  (rum/with-key (:id todo))))]])]
     
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
               :on-change #(insert! *session id {::done (not done)})}]
            [:label
             {:on-double-click #(reset! *editing true)}
             text]
            [:button.destroy
              {:on-click #(retract! *session id)}]]
          (when @*editing
            (todo-input
              {:*session *session
               :initial-text text
               :id id
               :on-save #(reset! *editing false)
               :on-stop #(reset! *editing false)}))])]}))

(def initial-session
  (-> (reduce o/add-rule (o/->session) (concat rules components))
      (o/insert ::global {::all-todos []
                          ::showing :all})
      o/fire-rules))

(def *session (atom initial-session))

