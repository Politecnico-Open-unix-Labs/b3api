(ns b3api.core
  (:require [org.httpkit.server :refer :all]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [cheshire.core :refer [generate-string parse-string]])
  (:gen-class))


(defonce channel-list (atom [])) ;; The list of open websockets
(defonce status (atom {}))       ;; The actual status, once read from the json
(defonce tokens (atom ["antani"]))

(defn log-append
  "Append a new message to log"
  [filename entry]
  (with-open [w (io/writer filename :append true)]
    (binding [*out* w] ;; Redirecting stdout to file
      (-> entry :raw println))))

(defn read-json
  "Retrieves the json from the data folder, returns a map"
  []
  (-> (str fs/*cwd* "/data/status.json")
      slurp
      (parse-string true)))

(defn write-json
  []
  (->> (generate-string @status)
       (spit (str fs/*cwd* "/data/status.json"))))

(defn broadcast!
  "Broadcasts some json data to all opened websockets"
  [new-data]
  (doseq [channel @channel-list]
    (send! channel new-data false)))

(defn async-handler [ring-request]
  ;; Unified API for WebSocket and HTTP long polling/streaming
  (let [big-json (generate-string @status)]
    (with-channel ring-request channel  ; Get an async channel from ring request
      ;; Some client tries to update - make sure it has the right token
      (on-receive channel (fn [data]
                            (let [data-map (parse-string data true)
                                  key (:key data-map)
                                  new-message (dissoc data-map :key)]
                              ;; Because authentication
                              (when (and key (some #{key} @tokens))
                                (println "New update!")
                                (broadcast! (generate-string new-message))
                                ;; todo: log
                                (swap! status merge data-map)
                                (write-json)))))
      (if (websocket? channel)        ; Websocket case - we track every channel
        (do
          (println "New client connected.")
          (swap! channel-list conj channel)
          (send! channel big-json false))
        (send! channel {:status  200
                        :headers {"Content-Type" "application/json"}
                        :body    big-json})))))

(defn -main
  "Main"
  [& args]
  (reset! status (read-json))
  (println "Starting server on 8080...")
  (run-server async-handler {:port 8080}))
