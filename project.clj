(defproject b3api "0.1.0-SNAPSHOT"
  :description "b3api"
  :url "https://github.com/Politecnico-Open-unix-Labs/b3api"
  :license {:name "GNU Affero General Public License"
            :url "http://www.gnu.org/licenses/agpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [cheshire "5.5.0"]
                 [me.raynes/fs "1.4.6"]
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [com.taoensso/encore "2.27.0"]
                 [com.taoensso/timbre "4.1.4"]
                 [http-kit "2.1.19"]]
  :main ^:skip-aot b3api.core
  :plugins [[lein-bin "0.3.5"]
            [lein-ancient "0.6.8"]]
  :bin {:name "b3api"}
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
