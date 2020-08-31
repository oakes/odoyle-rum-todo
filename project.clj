(defproject odoyle-rum-todo "0.1.0-SNAPSHOT"
  :aot [odoyle-rum-todo.start]
  :main odoyle-rum-todo.start
  :exclusions [cljsjs/react
               cljsjs/react-dom
               sablono
               ring/ring-devel])
