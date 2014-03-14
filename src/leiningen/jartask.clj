(ns leiningen.jartask
  (:require [leiningen.core.main :as main]
            [clojure.string :as str]
            [cemerick.pomegranate.aether :as aether]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as rt]
            [clojure.tools.reader.edn :as edn])
  (:import java.util.jar.JarFile
           java.io.PushbackReader
           java.io.StringReader))

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
  (->
   (resolve-coord project coord)
   (get coord)
   first
   meta
   :file))

(defn repeat-while
  "(repeatedly) calls f, while pred returns true. With no pred, calls f while it returns truthy. not lazy."
  ([f]
     (repeat-while identity f))
  ([pred f]
     (doall (take-while pred (repeatedly f)))))

(defn read-seq
  "Like clojure.core/read-string, but returns a seq of forms, reading as much as possible"
  [string & {:keys [safe?]
             :or {safe? true}}]
  (let [str-reader (rt/string-push-back-reader string)
        pushback-reader (rt/indexing-push-back-reader str-reader)]
    (repeat-while #(if safe?
                     (edn/read {:eof nil} pushback-reader)
                     (reader/read pushback-reader false nil true)))))

(defn load-project
  "Hacky way of eval'ing the project approxmiately the way lein does, until we get a patch in"
  ([contents profiles]
     (binding [*ns* (find-ns 'leiningen.core.project)]
       (try (->>
             contents
             (read-seq)
             (apply list 'do)
             (eval))

            (catch Exception e
              (println (.getMessage e))
              (throw (Exception. (format "Error reading project") e)))))
     (let [project (resolve 'leiningen.core.project/project)]
       (when-not project
         (throw (Exception. (format "project must define project map" project))))
       ;; return it to original state
       (ns-unmap 'leiningen.core.project 'project)
       (leiningen.core.project/init-profiles (leiningen.core.project/project-with-profiles @project) profiles)))
  ([contents] (load-project contents [:default])))

(defn project-from-jar
  "Given the local path to the jar file, read and return the project.clj"
  [jar-path]
  (with-open [jar (JarFile. jar-path)]
    (let [project-entry (.getEntry jar "project.clj")]
      (with-open [project-stream (.getInputStream jar project-entry)]
        (load-project (slurp project-stream))))))

;; (defn parse-coord [args]
;;   {:coord coord
;;    :rest-args rest-args})

(defn parse-coord [coord-str]
  (let [[_ name version] (re-find #"\[([./\w]+) (.+)\]" coord-str)]
    (println name)
    (println version)
    [(symbol name) (str version)]))

(defn parse-args [args]
  (let [[_ coord rest-args] (re-find #"(\[.+\])(.+)" (str/join " " args))
        rest-args (str/split rest-args #" ")]
    {:coord (parse-coord coord)
     :task (first rest-args)
     :task-args (rest rest-args)}))

(defn run [project task task-args]
  (main/apply-task project task task-args))

(defn jartask
  "Run the lein task contained in the project.clj for the given jar

  Usage:

  lein jartask [circleci/artifacts \"0.1.21\"] run"
  [jartask-project & args]
  (let [{:keys [coord task task-args]} (parse-args args)]
    (let [jar-path (jar-path jartask-project coord)
          project (project-from-jar jar-path)]
      (main/apply-task task project task-args))))
