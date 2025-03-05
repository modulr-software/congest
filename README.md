# congest
A custom self-hostable inngest service that runs on the jvm. built using clojure.

## Development

### Prerequisites

You need to have a config similar to ```merveillevaneck/clj-install```.

### Workflow

Open the project in your editor. Make sure you have a keyboard binding for clj-reload pointing to `clj-reload/reload`.

This project using jvm clojure.

- Clone the repo
- Create a file called ```dev/dev.clj``` that will serve as the namespace that all other namespaces are loaded into for development.
- Start an nrepl in the terminal: ```clojure -M:nrepl```
- Connect to the running nrepl via your editor. (For calva this is ```Ctrl-shift+c+c```).
- Open ```dev.clj``` and populate it with the content of ```dev.example```.
- Eval the namespace to the repl to test that it is running. (using Calva this is ```alt-enter```).


### Git contribution

All updates to the repo are managed using issues. Even feature additions.

We use gitflow. A.k.a. don't be a pleb....

Make new branches with a ```(feat|refactor|bugfix|hotfix)``` prefix. E.g. ```feat/sql-persistance-on-shutdown```.

Branches that contribute to features need to merge into their respective feature branches and not into main. Commits that are shared dependencies between features / refactors need to merge into main instead of their respective feature / refactor branches.

No PR's get merged without review.

Mark your work-in-progress PR's with ```WIP``` in the PR title.

Every PR needs a proper description. Please link the issue you are adressing and explain how the PR goes about fixing the issue / adding the required feature.

## Thats all folks!

# HAPPY HACKING!!!!

