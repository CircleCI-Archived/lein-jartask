(ns leiningen.jartask
  (:require [leiningen.core.main :as main]
            [leiningen.core.project :as project]
            [cemerick.pomegranate.aether :as aether]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs])
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

(defn jar-path
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

(defn extract-project
  "Extract the project.clj to dir. Dir must already exist"
  [jar dir]
  (with-open [jar (JarFile. jar)]
    (let [entry (.getEntry jar "project.clj")
          dest-name (.getName entry)
          dest-file (File. (str dir File/separator dest-name))
          dest-path (.toPath dest-file)]
      (Files/createDirectories (.getParent dest-path) empty-file-attrs)
      (with-open [i (.getInputStream jar entry)]
        (io/copy i dest-file)))))

(defmacro with-temp-jar-dir
  "Extracts the jar to a temp dir, binds 'path to the extracted path, runs the body, deletes the temp dir"
  [[path jar-path] & body]
  `(let [temp-dir# (create-temp-dir "jartask")]
     (try
       (let [_# (extract-project ~jar-path temp-dir#)
             ~path temp-dir#]
         ~@body)
       (finally
         (println "deleting temp-dir")
         (fs/delete-dir temp-dir#)))))

(defn parse-coord [coord-str]
  (let [[_ name version] (re-find #"\[([./\w]+) (.+)\]" coord-str)]
    [(symbol name) (str version)]))

(defn parse-args [args]
  (let [[_ coord rest-args] (re-find #"(\[.+\])(.+)" (str/join " " args))
        rest-args (str/split (str/trim rest-args) #" ")]
    {:coord (parse-coord coord)
     :task (first rest-args)
     :task-args (rest rest-args)}))

(defn run [project task task-args]
  (main/apply-task project task task-args))

(defn ^:no-project-needed ^:higher-order jartask
  "Run the lein task contained in the project.clj for the given jar

  Usage:

  lein jartask [circleci/artifacts \"0.1.21\"] run"
  [jartask-project & args]
  (let [{:keys [coord task task-args]} (parse-args args)]
    (let [jar-path (jar-path jartask-project coord)]
      (assert jar-path)
      (with-temp-jar-dir [temp-dir jar-path]
        (let [project-path (str temp-dir "/project.clj")
              project (-> (project/read project-path)
                          (update-in [:profiles :jartask :dependencies] conj coord)
                          (project/project-with-profiles)
                          (project/merge-profiles [:jartask]))]
          (main/apply-task task project task-args))))))
