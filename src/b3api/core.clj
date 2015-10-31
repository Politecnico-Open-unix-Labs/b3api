(ns b3api.core
  (:require [org.httpkit.server :refer :all]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [compojure.core :refer [defroutes GET POST]]
            [cheshire.core :refer [generate-string parse-string]])
  (:gen-class))


(defonce channel-list (atom [])) ;; The list of open websockets
(defonce status (atom {}))       ;; The actual status, once read from the json
(defonce tokens (atom []))       ;; List of master tokens


(defn log-append!
  "Append a new message to the log"
  [entry]
  (with-open [w (io/writer (str fs/*cwd* "/data/status.log") :append true)]
    (binding [*out* w] ;; Redirecting stdout to file
      (-> entry :raw println))))


(defn read-json
  "Reads the json from the data folder, returns a clojure data structure"
  [filename]
  (let [f (str fs/*cwd* "/data/" filename ".json")]
    (when (not (fs/exists? f))
      (spit f ""))
    (-> (slurp f)
        (parse-string true))))


(defn write-status-json!
  "Writes the status atom into the json status file"
  []
  (->> (generate-string @status)
       (spit (str fs/*cwd* "/data/status.json"))))


(defn broadcast!
  "Broadcasts some json data to all subscribed websockets"
  [new-data]
  (doseq [channel @channel-list]
    (send! channel new-data false)))


(defn update-status
  "Handle client messages, only when authenticated"
  [data]
  (let [data-map (parse-string data true)
        key (:key data-map)
        new-message (dissoc data-map :key)]
    ;; Because authentication
    (if (and key (some #{key} @tokens))
      (do
        (println "New update!")
        (broadcast! (generate-string new-message))
        ;; TODO: append to log file
        (swap! status merge new-message)
        (write-status-json!))
      (println "Not authenticated!"))))


(defn update-handler
  "Handle POST client messages"
  [ring-request]
  (let [data (slurp (:body ring-request))] ;; slurp because it is a stream
    (println data)
    (update-status data)))


(defn root-handler [ring-request]
  "Handles the websocket requests, with fallback on HTTP GET"
  (let [big-json (generate-string @status)]
    ;; Get an async channel from ring request
    (with-channel ring-request channel
      (if (websocket? channel)
        ;; Websocket case - we track every channel, to broadcast new messages
        (do
          (println "New client connected.")
          (swap! channel-list conj channel)
          (send! channel big-json false))
        ;; If HTTP just send down the json
        (send! channel {:status  200
                        :headers {"Content-Type" "application/json"}
                        :body    big-json}))
      (on-receive channel update-status)
      ;; On channel close just remove it from the subscribed channels
      (on-close channel (fn [status]
                          (println "Channel closed.")
                          (reset! channel-list (remove #(= % channel)
                                                       @channel-list)))))))


(defroutes all-routes
  (GET  "/" [] root-handler)     ;; Websocket + GET
  (POST "/" [] update-handler))  ;; POST for one-time updates or fallback
  ;; TODO: /hist


(defn -main
  "Load server data, start server."
  [& args]
  ;; Read jsons from file
  (reset! status (read-json "status"))
  (reset! tokens (read-json "tokens"))
  (println "Starting server on 8080...")
  ;; wrap-defaults is middleware magic: takes requests and routes them
  (run-server (wrap-defaults all-routes api-defaults)
              {:ip "localhost"
               :port 8080}))
