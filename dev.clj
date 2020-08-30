(require
  '[figwheel.main :as figwheel]
  '[odoyle-rum-todo.start-dev :refer [-main]])

(-main)
(figwheel/-main "--build" "dev")

