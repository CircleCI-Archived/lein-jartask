(ns leiningen.jartask
  (:require [leiningen.core.main :as main]
            [leiningen.core.project :as project]
            [cemerick.pomegranate.aether :as aether]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.java.io :as io])
  (:import java.util.jar.JarFile
           (java.io PushbackReader
                    StringReader
                    File)
           (java.nio.file Files
                          Path
                          attribute.FileAttribute)))

(defn project-repo-map
  "Returns the map of all repos. Use for resolving, not deploying"
  [project]
  (into {} (map (fn [[name repo]]
                  [name (leiningen.core.user/resolve-credentials repo)]) (:repositories project))))

(defn resolve-coord
  "Resolve a single coordinate"
  [project coord]
  (aether/resolve-dependencies :coordinates [coord] :repositories (project-repo-map project)))

(defn resolve-jar-path
  "Resolve the coordinates, returns the path to the jar file locally"
  [project coord]
  (->> (resolve-coord project coord)
       keys
       (filter #(= % coord))
       first
       meta
       :file))

(def empty-file-attrs
  ;; several Files/ methods need this
  (into-array FileAttribute []))

(defn create-temp-dir [prefix]
  (Files/createTempDirectory "foo" empty-file-attrs))

(defn extract-file-from-jar
  "Extract a file to dir. Dir must already exist"
  [jar dir filename]
  (with-open [jar (JarFile. jar)]
    (let [entry (.getEntry jar filename)
          dest-name (.getName entry)
          dest-file (File. (str dir File/separator dest-name))
          dest-path (.toPath dest-file)]
      (Files/createDirectories (.getParent dest-path) empty-file-attrs)
      (with-open [i (.getInputStream jar entry)]
        (io/copy i dest-file)))))

(defn parse-coord-str [coord-str]
  (let [[_ name version] (or (re-find #"\[([./\w]+) ([^\"]+)\]" coord-str)
                             (re-find #"\[([./\w]+) \"([^\"]+)\"\]" coord-str))]
    [(symbol name) (str version)]))

(defn parse-args [args]
  (let [[_ coord rest-args] (re-find #"^(\[.+\])(.+)" (str/join " " args))
        rest-args (when rest-args
                    (str/split (str/trim rest-args) #" "))]
    (when coord
      {:coord (parse-coord-str coord)
       :task (first rest-args)
       :task-args (rest rest-args)})))

(defn exec [jartask-project {:keys [coord task task-args]
                             :as args}]
  (let [jar-path (resolve-jar-path jartask-project coord)
        _ (when-not jar-path
            (main/abort (format "couldn't resolve %s" coord)))
        project-dir (create-temp-dir "jartask")
        _ (extract-file-from-jar jar-path project-dir "project.clj")
        project-path (str/join "/" [project-dir "project.clj"])
        project (-> (project/read project-path)
                    (update-in [:profiles :jartask :dependencies] conj coord)
                    (project/project-with-profiles)
                    (project/merge-profiles [:jartask]))]
    (main/apply-task task project task-args)))

(defn run [project task task-args]
  (main/apply-task project task task-args))

(defn ^:no-project-needed ^:higher-order jartask
  "Run the lein task contained in the project.clj for the given jar

  Usage:

  lein jartask [circleci/artifacts \"0.1.21\"] run"
  [jartask-project & args]
  (if-let [args (parse-args args)]
    (exec jartask-project args)
    (main/abort "couldn't parse arguments")))
