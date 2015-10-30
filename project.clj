(defproject b3api "0.1.0-SNAPSHOT"
  :description "b3api"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [cheshire "5.5.0"]
                 [me.raynes/fs "1.4.6"]
                 [stylefruits/gniazdo "0.4.1"]
                 [http-kit "2.1.18"]]
  :main ^:skip-aot b3api.core
  :plugins [[lein-bin "0.3.5"]]
  :bin {:name "b3api"}
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
