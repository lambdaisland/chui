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

(deftest succeeding-test
  (is true)
  (is (= 1 1)))

(deftest aa-test
  (is (= 1 1))
  (is (= [{:x 123}] [{:x 124}]))


  (is (= {:person/name "Arne"
          :person/Age 37
          :person/books
          [{:book/title "The Art of Motorcycle Maintenance"}
           {:book/title "The Art of Motorcycle Maintenance"}
           {:book/title "The Art of Motorcycle Maintenance"}
           {:book/title "The Art of Motorcycle Maintenance"}]
          :user/permissions #{:can-read :can-write}}
         {:person/name "Arne"
          :person/Age 37
          :person/books
          [{:book/title "Zend and  Art of Motorcycle Maintenance"}
           {:book/title "The Art of Motorcycle Maintenance"}
           {:book/title "The Art of Motorcycle Maintenance"}
           {:book/title "The Art of Motorcycle Maintenance"}]
          :user/permissions #{:can-read :can-write}}))
  )

(deftest bb-test
  (testing "hello world"
    (testing "without async"
      (is (= :with :without))))
  (testing "with async"
    (async
     done
     (js/setTimeout #(do
                       (is (= 123 124))
                       (done))
                    1000))))

(deftest error-test
  (is (throw (ex-info {:foo :baz} "hello"))))

(comment
  (cljs-test-display.core/init!)
  (cljs.test/run-tests))
