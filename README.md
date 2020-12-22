This project shows how to make a todo list with [O'Doyle Rum](https://github.com/oakes/odoyle-rum). It is based on [reframe's todomvc project](https://github.com/day8/re-frame/tree/master/examples/todomvc). Unlike that project, though, this one does server-side rendering and persists the todos in a server-side session rather than local storage.

## Development

* Install [the Clojure CLI tool](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools)
* To develop with figwheel: `clj -M:dev:cljs`
* To build a JAR file: `clj -M:prod:cljs`
