(defproject b3api "0.1.0-SNAPSHOT"
  :description "A minimal publish-subscribe server."
  :url "https://github.com/Politecnico-Open-unix-Labs/b3api"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [cheshire "5.6.1"]
                 [me.raynes/fs "1.4.6"]
                 [compojure "1.5.0"]
                 [ring/ring-defaults "0.2.0"]
                 [com.taoensso/encore "2.50.0"]
                 [com.taoensso/timbre "4.3.1"]
                 [http-kit "2.1.19"]]
  :main ^:skip-aot b3api.core
  :plugins [[lein-bin "0.3.5"]
            [lein-ancient "0.6.8"]]
  :bin {:name "b3api"}
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
