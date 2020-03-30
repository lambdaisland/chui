(ns lambdaisland.chui-demo.a-test
  (:require [clojure.test :refer [deftest testing is are async use-fixtures]]
            [lambdaisland.glogi :as log]))

(use-fixtures :each
  {:id 1
   :before (fn []
             (log/info :each :before))
   :after  (fn []
             (log/info :each :after))}

  {:id 2
   :before (fn []
             (log/info :each2 :before))
   :after  (fn []
             (log/info :each2 :after))})

(use-fixtures :once
  {:id 3
   :before (fn []
             (async done
                    (log/info :once :before)
                    (done)))
   :after (fn []
            (async done
                   (log/info :once :after)
                   (done)))})

(deftest aa-test
  (is (= 123 124)))

(deftest bb-test
  (testing "without async"
    (is (= :with :without)))
  (testing "with async"
    (async
     done
     (js/setTimeout #(do
                       (is (= 123 124))
                       (done))
                    1000))))

(comment
  (cljs-test-display.core/init!)
  (cljs.test/run-tests))
