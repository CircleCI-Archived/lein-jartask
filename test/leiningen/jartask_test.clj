(ns leiningen.jartask-test
  (:require [clojure.test :refer :all]
            [leiningen.jartask :as jartask]))

(deftest parse-coord-works
  (is (= '[foo/bar "1.2.3"] (jartask/parse-coord-str "[foo/bar 1.2.3]")))
  (testing "handles optional parens around version"
    (is (= '[foo/bar "1.2.3"] (jartask/parse-coord-str "[foo/bar \"1.2.3\"]"))))
  (testing "handles dashes in name"
    (is (= '[foo/foo-bar "1.2.3"] (jartask/parse-coord-str "[foo/foo-bar \"1.2.3\"]")))))

(deftest parse-args-works
  (is (= {:coord '[foo/bar "1.2.3"]
          :task "run"
          :task-args ["baz" "1"]}) (jartask/parse-args ["[foo/bar" "1.2.3]" "run" "baz" "1"])))

(deftest parse-args-doesnt-throw
  (jartask/parse-args []))
