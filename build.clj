(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'com.modulr-software/congest)
(def version "0.1.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file
          :basis basis
          :lib lib
          :version version
          :pom-data {:group (namespace lib)
                     :artifact (name lib)
                     :version version
                     :description "congest"
                     :url "https://github.com/modulr-software/congest"
                     :licenses [{:name "MIT"
                                 :url "https://opensource.org/licenses/MIT"}]}}))

(defn install [_]
  (b/install {:basis basis
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir}))
