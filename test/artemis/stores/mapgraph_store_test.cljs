(ns artemis.stores.mapgraph-store-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [artemis.core :as a]
            [artemis.document :as d]
            [artemis.stores.mapgraph.core :refer [create-store]]))

(def test-queries
  {:basic
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 3
               :nullField   nil}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 3
                :nullField   nil
                ::cache      "root"}}}

   :args
   {:query    (d/parse-document
                "{
                   id
                   stringField(arg: 1)
                   numberField
                   nullField
                 }")
    :result   {:id          "abcd"
               :stringField "The arg was 1"
               :numberField 3
               :nullField   nil}
    :entities {"root"
               {:id                        "abcd"
                "stringField({\"arg\":1})" "The arg was 1"
                :numberField               3
                :nullField                 nil
                ::cache                    "root"}}}

   :aliased
   {:query    (d/parse-document
                "{
                   id
                   aliasedField: stringField
                   numberField
                   nullField
                 }")
    :result   {:id           "abcd"
               :aliasedField "this is a string"
               :numberField  3
               :nullField    nil}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 3
                :nullField   nil
                ::cache      "root"}}}

   :aliased-with-args
   {:query    (d/parse-document
                "{
                   id
                   aliasedField1: stringField(arg:1)
                   aliasedField2: stringField(arg:2)
                   numberField
                   nullField
                 }")
    :result   {:id            "abcd"
               :aliasedField1 "The arg was 1"
               :aliasedField2 "The arg was 2"
               :numberField   3
               :nullField     nil}
    :entities {"root"
               {:id                        "abcd"
                "stringField({\"arg\":1})" "The arg was 1"
                "stringField({\"arg\":2})" "The arg was 2"
                :numberField               3
                :nullField                 nil
                ::cache                    "root"}}}

   :with-vars
   {:query      (d/parse-document
                  "{
                     id
                     stringField(arg: $stringArg)
                     numberField(intArg: $intArg, floatArg: $floatArg)
                     nullField
                   }")
    :input-vars {:intArg    5
                 :floatArg  3.14
                 :stringArg "This is a string"}
    :result     {:id          "abcd"
                 :stringField "This worked"
                 :numberField 5
                 :nullField   nil}
    :entities   {"root"
                 {:id                                             "abcd"
                  :nullField                                      nil
                  "numberField({\"intArg\":5,\"floatArg\":3.14})" 5
                  "stringField({\"arg\":\"This is a string\"})"   "This worked"
                  ::cache                                         "root"}}}

   :default-vars
   {:query      (d/parse-document
                  "query someBigQuery(
                     $stringArg: String = \"This is a default string\"
                     $intArg: Int
                     $floatArg: Float
                   ) {
                     id
                     stringField(arg: $stringArg)
                     numberField(intArg: $intArg, floatArg: $floatArg)
                     nullField
                   }")
    :input-vars {:intArg   5
                 :floatArg 3.14}
    :result     {:id          "abcd"
                 :stringField "This worked"
                 :numberField 5
                 :nullField   nil}
    :entities   {"root"
                 {:id                                                   "abcd"
                  :nullField                                            nil
                  "numberField({\"intArg\":5,\"floatArg\":3.14})"       5
                  "stringField({\"arg\":\"This is a default string\"})" "This worked"
                  ::cache                                               "root"}}}

   :directives
   {:query    (d/parse-document
                "{
                   id
                   firstName @include(if: true)
                   lastName @upperCase
                   birthDate @dateFormat(format: \"DD-MM-YYYY\")
                 }")
    :result   {:id        "abcd"
               :firstName "James"
               :lastName  "BOND"
               :birthDate "20-05-1940"}
    :entities {"root"
               {:id                                                 "abcd"
                :firstName                                          "James"
                "lastName@upperCase"                                "BOND"
                "birthDate@dateFormat({\"format\":\"DD-MM-YYYY\"})" "20-05-1940"
                ::cache                                             "root"}}}

   :nested
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   nestedObj {
                     id
                     stringField
                     numberField
                     nullField
                   }
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 3
               :nullField   nil
               :nestedObj   {:id          "abcde"
                             :stringField "this is a string too"
                             :numberField 3
                             :nullField   nil
                             :__typename  "object"}}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 3
                :nullField   nil
                :nestedObj   {:artemis.mapgraph/ref "abcde"}
                ::cache      "root"}
               "abcde"
               {:object/id          "abcde"
                :object/stringField "this is a string too"
                :object/numberField 3
                :object/nullField   nil
                :__typename         "object"}}}

   :nested-no-id
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   nestedObj {
                     stringField
                     numberField
                     nullField
                   }
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 3
               :nullField   nil
               :nestedObj   {:stringField "this is a string too"
                             :numberField 3
                             :nullField   nil
                             :__typename  "object"}}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 3
                :nullField   nil
                :nestedObj   {:artemis.mapgraph/ref "root.nestedObj"}
                ::cache      "root"}
               "root.nestedObj"
               {:object/stringField "this is a string too"
                :object/numberField 3
                :object/nullField   nil
                :__typename         "object"
                ::cache             "root.nestedObj"}}}

   :nested-with-args
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   nestedObj(arg:\"val\") {
                     stringField
                     numberField
                     nullField
                   }
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 3
               :nullField   nil
               :nestedObj   {:stringField "this is a string too"
                             :numberField 3
                             :nullField   nil
                             :__typename  "object"}}
    :entities {"root"
               {:id                            "abcd"
                :stringField                   "this is a string"
                :numberField                   3
                :nullField                     nil
                "nestedObj({\"arg\":\"val\"})" {:artemis.mapgraph/ref "root.nestedObj({\"arg\":\"val\"})"}
                ::cache                        "root"}
               "root.nestedObj({\"arg\":\"val\"})"
               {:object/stringField "this is a string too"
                :object/numberField 3
                :object/nullField   nil
                :__typename         "object"
                ::cache             "root.nestedObj({\"arg\":\"val\"})"}}}

   :nested-array
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   nestedArray {
                     id
                     stringField
                     numberField
                     nullField
                   }
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 1
               :nullField   nil
               :nestedArray [{:id          "abcde"
                              :stringField "this is a string too"
                              :numberField 2
                              :nullField   nil
                              :__typename  "object"}
                             {:id          "abcdef"
                              :stringField "this is a string also"
                              :numberField 3
                              :nullField   nil
                              :__typename  "object"}]}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 1
                :nullField   nil
                :nestedArray [{:artemis.mapgraph/ref "abcde"}
                              {:artemis.mapgraph/ref "abcdef"}]
                ::cache      "root"}
               "abcde"
               {:object/id          "abcde"
                :object/stringField "this is a string too"
                :object/numberField 2
                :object/nullField   nil
                :__typename         "object"}
               "abcdef"
               {:object/id          "abcdef"
                :object/stringField "this is a string also"
                :object/numberField 3
                :object/nullField   nil
                :__typename         "object"}}}

   :nested-array-with-null
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   nestedArray {
                     id
                     stringField
                     numberField
                     nullField
                   }
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 1
               :nullField   nil
               :nestedArray [{:id          "abcde"
                              :stringField "this is a string too"
                              :numberField 2
                              :nullField   nil
                              :__typename  "object"}
                             nil]}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 1
                :nullField   nil
                :nestedArray [{:artemis.mapgraph/ref "abcde"} nil]
                ::cache      "root"}
               "abcde"
               {:object/id          "abcde"
                :object/stringField "this is a string too"
                :object/numberField 2
                :object/nullField   nil
                :__typename         "object"}}}

   :deeply-nested-array
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   nestedArray {
                     stringField
                     numberField
                     nullField
                     deeplyNestedArray {
                       numberField
                       stringField
                     }
                   }
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 1
               :nullField   nil
               :nestedArray [{:stringField       "this is a string too"
                              :numberField       2
                              :nullField         nil
                              :deeplyNestedArray [{:numberField 10
                                                   :stringField "Foo"}
                                                  {:numberField 20
                                                   :stringField "Bar"}]
                              :__typename        "object"}
                             {:stringField       "this is a string also"
                              :numberField       3
                              :deeplyNestedArray [{:numberField 30
                                                   :stringField "Baz"}
                                                  {:numberField 40
                                                   :stringField "Boo"}]
                              :nullField         nil
                              :__typename        "object"}

                             {:stringField       "this is a string, man"
                              :numberField       6
                              :deeplyNestedArray []
                              :nullField         nil
                              :__typename        "object"}]}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 1
                :nullField   nil
                :nestedArray [{:artemis.mapgraph/ref "root.nestedArray.0"}
                              {:artemis.mapgraph/ref "root.nestedArray.1"}
                              {:artemis.mapgraph/ref "root.nestedArray.2"}]
                ::cache      "root"}
               "root.nestedArray.0"
               {:object/stringField "this is a string too"
                :object/numberField 2
                :object/nullField   nil
                :object/deeplyNestedArray [{:artemis.mapgraph/ref "root.nestedArray.0.deeplyNestedArray.0"}
                                           {:artemis.mapgraph/ref "root.nestedArray.0.deeplyNestedArray.1"}]
                :__typename         "object"
                ::cache             "root.nestedArray.0"}
               "root.nestedArray.1"
               {:object/stringField "this is a string also"
                :object/numberField 3
                :object/nullField   nil
                :object/deeplyNestedArray [{:artemis.mapgraph/ref "root.nestedArray.1.deeplyNestedArray.0"}
                                           {:artemis.mapgraph/ref "root.nestedArray.1.deeplyNestedArray.1"}]
                :__typename         "object"
                ::cache             "root.nestedArray.1"}
               "root.nestedArray.2"
               {:object/stringField       "this is a string, man"
                :object/numberField       6
                :object/nullField         nil
                :object/deeplyNestedArray []
                :__typename               "object"
                ::cache                   "root.nestedArray.2"}
               "root.nestedArray.0.deeplyNestedArray.0"
               {:numberField 10
                :stringField "Foo"
                ::cache      "root.nestedArray.0.deeplyNestedArray.0"}
               "root.nestedArray.0.deeplyNestedArray.1"
               {:numberField 20
                :stringField "Bar"
                ::cache      "root.nestedArray.0.deeplyNestedArray.1"}
               "root.nestedArray.1.deeplyNestedArray.0"
               {:numberField 30
                :stringField "Baz"
                ::cache      "root.nestedArray.1.deeplyNestedArray.0"}
               "root.nestedArray.1.deeplyNestedArray.1"
               {:numberField 40
                :stringField "Boo"
                ::cache      "root.nestedArray.1.deeplyNestedArray.1"}}}

   :nested-array-without-ids
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   nestedArray {
                     stringField
                     numberField
                     nullField
                   }
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 1
               :nullField   nil
               :nestedArray [{:stringField "this is a string too"
                              :numberField 2
                              :nullField   nil
                              :__typename  "object"}
                             {:stringField "this is a string also"
                              :numberField 3
                              :nullField   nil
                              :__typename  "object"}]}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 1
                :nullField   nil
                :nestedArray [{:artemis.mapgraph/ref "root.nestedArray.0"}
                              {:artemis.mapgraph/ref "root.nestedArray.1"}]
                ::cache      "root"}
               "root.nestedArray.0"
               {:object/stringField "this is a string too"
                :object/numberField 2
                :object/nullField   nil
                :__typename         "object"
                ::cache             "root.nestedArray.0"}
               "root.nestedArray.1"
               {:object/stringField "this is a string also"
                :object/numberField 3
                :object/nullField   nil
                :__typename         "object"
                ::cache             "root.nestedArray.1"}}}

   :nested-array-with-nulls-and-no-ids
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   nestedArray {
                     stringField
                     numberField
                     nullField
                   }
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 1
               :nullField   nil
               :nestedArray [nil
                             {:stringField "this is a string also"
                              :numberField 3
                              :nullField   nil
                              :__typename  "object"}]}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 1
                :nullField   nil
                :nestedArray [nil
                              {:artemis.mapgraph/ref "root.nestedArray.1"}]
                ::cache      "root"}
               "root.nestedArray.1"
               {:object/stringField "this is a string also"
                :object/numberField 3
                :object/nullField   nil
                :__typename         "object"
                ::cache             "root.nestedArray.1"}}}

   :simple-array
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   simpleArray
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 3
               :nullField   nil
               :simpleArray ["one" "two" "three"]}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 3
                :nullField   nil
                :simpleArray ["one" "two" "three"]
                ::cache      "root"}}}

   :simple-array-with-nulls
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   simpleArray
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 3
               :nullField   nil
               :simpleArray [nil "two" "three"]}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 3
                :nullField   nil
                :simpleArray [nil "two" "three"]
                ::cache      "root"}}}

   :obj-in-different-paths
   {:query    (d/parse-document
                "{
                   id
                   object1 {
                     id
                     stringField
                   }
                   object2 {
                     id
                     numberField
                   }
                 }")
    :result   {:id      "a"
               :object1 {:id          "aa"
                         :stringField "this is a string"
                         :__typename  "object"}
               :object2 {:id          "aa"
                         :numberField 1
                         :__typename  "object"}}

    :entities {"root"
               {:id      "a"
                :object1 {:artemis.mapgraph/ref "aa"}
                :object2 {:artemis.mapgraph/ref "aa"}
                ::cache  "root"}
               "aa"
               {:object/id          "aa"
                :object/stringField "this is a string"
                :object/numberField 1
                :__typename         "object"}}}

   :obj-in-different-array-paths
   {:query    (d/parse-document
                "{
                   id
                   array1 {
                     id
                     stringField
                     obj {
                       id
                       stringField
                     }
                   }
                   array2 {
                     id
                     stringField
                     obj {
                       id
                       numberField
                     }
                   }
                 }")
    :result   {:id     "a"
               :array1 [{:id          "aa"
                         :stringField "this is a string"
                         :obj         {:id          "aaa"
                                       :stringField "string"
                                       :__typename  "nested-object"}
                         :__typename  "object"}]
               :array2 [{:id          "ab"
                         :stringField "this is a string too"
                         :obj         {:id          "aaa"
                                       :numberField 1
                                       :__typename  "nested-object"}
                         :__typename  "object"}]}

    :entities {"root"
               {:id     "a"
                :array1 [{:artemis.mapgraph/ref "aa"}]
                :array2 [{:artemis.mapgraph/ref "ab"}]
                ::cache "root"}
               "aa"
               {:object/id          "aa"
                :object/stringField "this is a string"
                :object/obj         {:artemis.mapgraph/ref "aaa"}
                :__typename         "object"}
               "ab"
               {:object/id          "ab"
                :object/stringField "this is a string too"
                :object/obj         {:artemis.mapgraph/ref "aaa"}
                :__typename         "object"}
               "aaa"
               {:nested-object/id          "aaa"
                :nested-object/stringField "string"
                :nested-object/numberField 1
                :__typename                "nested-object"}}}

   :nested-object-returning-null
   {:query    (d/parse-document
                "{
                   id
                   stringField
                   numberField
                   nullField
                   nestedObj {
                     id
                     stringField
                     numberField
                     nullField
                   }
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 3
               :nullField   nil
               :nestedObj   nil}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 3
                :nullField   nil
                :nestedObj   nil
                ::cache      "root"}}}

     :union-array
     {:query    (d/parse-document
                  "{
                     search(text: \"a\") {
                       ... on object {
                         id
                         stringField
                       }
                       ... on otherobject {
                         id
                         numberField
                       }
                     }
                   }")
      :result   {:search
                 [{:id          "abcd"
                   :stringField "this is a string"
                   :__typename  "object"}
                  {:id          "efgh"
                   :numberField 3
                   :__typename  "otherobject"}]}
      :entities {"root"
                 {"search({\"text\":\"a\"})" [{:artemis.mapgraph/ref "abcd"}
                                              {:artemis.mapgraph/ref "efgh"}]
                  ::cache                    "root"}
                 "abcd"
                 {:object/id          "abcd"
                  :object/stringField "this is a string"
                  :__typename         "object"}
                 "efgh"
                 {:otherobject/id          "efgh"
                  :otherobject/numberField 3
                  :__typename              "otherobject"}}}

     :union-array-no-id
     {:query    (d/parse-document
                  "{
                     search(text: \"a\") {
                       ... on someobject {
                         stringField
                       }
                     }
                   }")
      :result   {:search
                 [{:stringField "this is a string"
                   :__typename  "someobject"}
                  {:stringField "this is another string"
                   :__typename  "someobject"}]}
      :entities {"root"
                 {"search({\"text\":\"a\"})" [{:artemis.mapgraph/ref "root.search({\"text\":\"a\"}).0"}
                                              {:artemis.mapgraph/ref "root.search({\"text\":\"a\"}).1"}]
                  ::cache                    "root"}
                 "root.search({\"text\":\"a\"}).0"
                 {:someobject/stringField "this is a string"
                  :__typename             "someobject"
                  ::cache                 "root.search({\"text\":\"a\"}).0"}
                 "root.search({\"text\":\"a\"}).1"
                 {:someobject/stringField "this is another string"
                  :__typename             "someobject"
                  ::cache                 "root.search({\"text\":\"a\"}).1"}}}

     :nested-union
     {:query    (d/parse-document
                  "{
                     id
                     stringField
                     unionObj {
                       ... on object {
                         id
                         numberField
                         stringField
                       }
                       ... on otherobject {
                         id
                         stringField
                       }
                     }
                   }")
      :result   {:id          "a"
                 :stringField "this is a string"
                 :unionObj    {:id          "abcd"
                               :stringField "this is a string"
                               :numberField 3
                               :__typename  "object"}}
      :entities {"root"
                 {:id          "a"
                  :stringField "this is a string"
                  :unionObj    {:artemis.mapgraph/ref "abcd"}
                  ::cache      "root"}
                 "abcd"
                 {:object/id          "abcd"
                  :object/stringField "this is a string"
                  :object/numberField 3
                  :__typename         "object"}}}

     :nested-union-no-id
     {:query    (d/parse-document
                  "{
                     id
                     stringField
                     unionObj {
                       ... on someobject {
                         numberField
                         stringField
                       }
                       ... on someotherobject {
                         stringField
                       }
                     }
                   }")
      :result   {:id          "a"
                 :stringField "this is a string"
                 :unionObj    {:stringField "this is a string"
                               :numberField 3
                               :__typename  "someobject"}}
      :entities {"root"
                 {:id          "a"
                  :stringField "this is a string"
                  :unionObj    {:artemis.mapgraph/ref "root.unionObj"}
                  ::cache      "root"}
                 "root.unionObj"
                 {:someobject/stringField "this is a string"
                  :someobject/numberField 3
                  :__typename             "someobject"
                  ::cache                 "root.unionObj"}}}

   :fragments
   {:query    (d/parse-document
                "query SomeQuery {
                   id
                   ...OtherFields
                 }

                 fragment OtherFields on object {
                   stringField
                   numberField
                 }")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 3}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 3
                ::cache      "root"}}}

   :nested-fragments
   {:query    (d/parse-document
                "query SomeQuery {
                   id
                   nestedObj {
                     id
                     ...OtherFields
                   }
                 }

                 fragment OtherFields on object {
                   stringField
                   numberField
                 }")
    :result   {:id        "abcd"
               :nestedObj {:id          "abcde"
                           :stringField "this is a string"
                           :numberField 3
                           :__typename  "object"}}
    :entities {"root"
               {:id        "abcd"
                :nestedObj {:artemis.mapgraph/ref "abcde"}
                ::cache    "root"}
               "abcde"
               {:object/id          "abcde"
                :object/stringField "this is a string"
                :object/numberField 3
                :__typename         "object"}}}

   :chained-fragments
   {:query    (d/parse-document
                "query SomeQuery {
                   id
                   ...OtherFields
                 }

                 fragment OtherFields on object {
                   stringField
                   ...EvenOtherFields
                 }

                fragment EvenOtherFields on object {
                   numberField
                }
                ")
    :result   {:id          "abcd"
               :stringField "this is a string"
               :numberField 3}
    :entities {"root"
               {:id          "abcd"
                :stringField "this is a string"
                :numberField 3
                ::cache      "root"}}}
})

(defn- id-fn [o]
  (let [k (first (filter #(% o) [:object/id :nested-object/id :otherobject/id]))]
    (get o k)))

(defn write-test [k]
  (testing (str "testing normalized cache persistence for query type: " k)
    (let [{:keys [query input-vars result entities]} (get test-queries k)
          initial-store (create-store :id-fn id-fn
                                      :cache-key ::cache)
          new-store (a/write initial-store {:data result} query input-vars)]
      (is (= entities (:entities new-store))))))

(deftest test-cache-persistence
  (doseq [test-query (keys test-queries)]
    (write-test test-query)))

(defn read-test [k]
  (testing (str "testing normalized cache querying for query type: " k)
    (let [{:keys [query input-vars result entities]} (get test-queries k)
          store (create-store :id-fn id-fn
                              :entities entities
                              :cache-key ::cache)
          response (a/read store query input-vars)]
      (is (= {:data result} response)))))

(deftest test-cache-reading
  (doseq [test-query (keys test-queries)]
    (read-test test-query)))

(def test-fragments
  {:basic
   {:fragment    (d/parse-document
                   "fragment A on object {
                      stringField
                    }")
    :entities    {"abcde"
                  {:id          "abcde"
                   :stringField "this is a string"
                   :numberField 3
                   :nullField   nil
                   ::cache      "root"}}
    :entity      "abcde"
    :write-data  {:stringField "this is a different string"}
    :read-result {:stringField "this is a string"}}

   :multiple-fields
   {:fragment    (d/parse-document
                   "fragment A on object {
                      numberField
                      stringField
                    }")
    :entities    {"abcde"
                  {:object/id          "abcde"
                   :object/stringField "this is a string"
                   :object/numberField 3}}
    :entity      "abcde"
    :write-data  {:stringField "this is a different string"
                  :numberField 4}
    :read-result {:stringField "this is a string"
                  :numberField 3}}})

(defn write-fragment-test [k]
  (testing (str "testing normalized cache persistence for fragment type: " k)
    (let [{:keys [fragment entity write-data entities]} (get test-fragments k)
          initial-store (create-store :id-fn id-fn
                                      :entities entities
                                      :cache-key ::cache)
          old-ent (get (:entities initial-store) entity)
          new-store (a/write-fragment initial-store write-data fragment entity)
          new-ent (get (:entities new-store) entity)]
      (is (= new-ent (merge old-ent new-ent))))))

(deftest test-cache-fragment-persistence
  (doseq [test-fragment (keys test-fragments)]
    (write-fragment-test test-fragment)))

(defn read-fragment-test [k]
  (testing (str "testing normalized cache reading for fragment type: " k)
    (let [{:keys [fragment entity entities read-result]} (get test-fragments k)
          store (create-store :id-fn id-fn
                              :entities entities
                              :cache-key ::cache)
          response (a/read-fragment store fragment entity)]
      (is (= {:data read-result} response)))))

(deftest test-cache-fragment-reading
  (doseq [test-fragment (keys test-fragments)]
    (read-fragment-test test-fragment)))
