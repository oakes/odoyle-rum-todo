(ns odoyle-rum-todo.core
  (:require [rum.core :as rum]
            [odoyle.rules :as o]
            [odoyle.rum :as orum]
            [clojure.spec.alpha :as s]))

(s/def ::text string?)
(s/def ::done boolean?)
(s/def ::todo (s/keys :req-un [::text ::done]))
(s/def ::all-todos (s/coll-of ::todo))
(s/def ::next-id integer?)
(s/def ::showing #{:all :active :completed})
(s/def ::upsert-todo integer?)

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
               o/fire-rules))))

(def rules
  (o/ruleset
    {::get-todo-item
     [:what
      [id ::text text]
      [id ::done done]
      ;; insert event that triggers the update-next-id rule.
      ;; note that there is nothing special about an "event".
      ;; it is just a fact that we insert in order to trigger
      ;; another rule, so we can keep a separation of concerns.
      :then
      (-> o/*session*
          (o/insert ::event ::upsert-todo id)
          o/reset!)
      ;; refresh the list of todos.
      ;; we use :then-finally because it runs
      ;; after todos are inserted *and* retracted.
      ;; :then blocks are only run after insertions.
      :then-finally
      (-> o/*session*
          refresh-all-todos
          o/reset!)]

     ::update-next-id
     [:what
      [::event ::upsert-todo id]
      [::global ::next-id next-id {:then false}]
      :when
      (>= id next-id)
      :then
      (-> o/*session*
          (o/insert ::global ::next-id (inc id))
          o/reset!)]
     
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
     [:what
      [::global ::next-id next-id]
      :then
      (let [{:keys [*session initial-text id on-finish]} (orum/prop)
            *text (orum/atom initial-text)
            text @*text
            next-id (or id next-id) ;; if there is an id in the prop, we are editing an existing todo
            on-finish (or on-finish #(reset! *text ""))]
        [:input {:type "text"
                 :class "edit"
                 :placeholder (if id
                                "Enter your edit"
                                "What needs to be done?")
                 :autoFocus true
                 :value text
                 :on-blur on-finish
                 :on-change (fn [e]
                              (reset! *text (-> e .-target .-value)))
                 :on-key-down (fn [e]
                                (case (.-keyCode e)
                                  13
                                  (let [todo (if id
                                               {::text text}
                                               ;; if the todo is new, set ::done as well
                                               {::text text ::done false})]
                                    (insert! *session next-id todo)
                                    (on-finish))
                                  27
                                  (on-finish)
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
               :on-finish #(reset! *editing false)}))])]}))

(def initial-session
  (-> (reduce o/add-rule (o/->session) (concat rules components))
      (o/insert ::global {::all-todos []
                          ::showing :all
                          ::next-id 0})
      o/fire-rules))

(defonce *session (atom initial-session))

;; when figwheel reloads this file,
;; get all the facts from the previous session
;; and insert them into the new session
;; so we don't wipe the state clean every time
(swap! *session
  (fn [session]
    (->> (o/query-all session)
         (reduce o/insert initial-session)
         o/fire-rules)))

