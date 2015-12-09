(ns b3api.core
  (:require [clojure.tools.logging :as log]
            [org.httpkit.server :refer :all]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [compojure.core :refer [defroutes GET POST]]
            [taoensso.encore :as enc :refer [if-lets update-in* reset-in!]]
            [cheshire.core :refer [generate-string parse-string]])
  (:gen-class))


(defonce channel-list (atom [])) ;; The list of open websockets
(defonce status       (atom {})) ;; The actual status, once read from the json
(defonce tokens       (atom {})) ;; Map of tokens:authorized-path
(defonce o            (Object.)) ;; The global lock object


(defn deep-merge*
  "A better merge function. Semantics: deep-merges until there's an empty map.
  TODO: remove keys having an empty map as value."
  [& maps]
  (let [f (fn [old new]
            (if (and (map? old) (map? new) (not (empty? new)))
              (merge-with deep-merge* old new)
              new))]
    (if (every? map? maps)
      (apply merge-with f maps)
      (last maps))))


(defn log-append!
  "Append a new message to the log file"
  [entry]
  (with-open [w (io/writer (str fs/*cwd* "/data/status.log") :append true)]
    (binding [*out* w] ;; Redirecting stdout to file
      (-> entry :raw log/info))))


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
  (locking o
    (->> (generate-string @status)
         (spit (str fs/*cwd* "/data/status.json")))))


(defn broadcast!
  "Broadcasts some json data to all subscribed websockets"
  [new-data]
  (doseq [channel @channel-list]
    (send! channel new-data false)))


(defn update-status
  "Handle client messages, only when authenticated"
  [data]
  (let [data-map  (parse-string data true)]
    (when-not (empty? data-map) ;; Ignoring heartbeats - empty maps
      ;; If any of the let bindings evals to nil -> message is not authenticated
      ;; Checking that:
      (if-lets
       [key       (keyword (:key data-map)) ;; 1. key is provided
        valid?    (contains? @tokens key)   ;; 2. key is valid
        auth-path (map keyword (get @tokens key))
        new-data  (get-in (dissoc data-map :key)
                          auth-path)        ;; 3. new is on the auth path of the key
        old-data  (get-in @status auth-path {})
        new-msg   (update-in* {} auth-path #(do %& new-data))]
       (do
         (log/info (str "New update: " (generate-string new-msg)))
         ;; Broadcasting only the authorized part
         (broadcast! (generate-string new-msg))
         ;; TODO: append to log file
         ;; Deep merging the authorized branch of the maps tree, and writing to file
         (reset-in! status auth-path (deep-merge* old-data new-data))
         (write-status-json!))
       (log/warn "Not authenticated!")))))


(defn update-handler
  "Handle POST client messages"
  [ring-request]
  (let [data (slurp (:body ring-request))] ;; slurp because it is a stream
    (log/info data)
    (update-status data)))


(defn root-handler
  "Handles the websocket requests, with fallback on HTTP GET"
  [ring-request]
  (let [big-json (generate-string @status)]
    ;; Get an async channel from ring request
    (with-channel ring-request channel
      (if (websocket? channel)
        ;; Websocket case - we track every channel, to broadcast new messages
        (do
          (log/info "New client connected.")
          (swap! channel-list conj channel)
          (send! channel big-json false))
        ;; If HTTP just send down the json
        (send! channel {:status  200
                        :headers {"Content-Type" "application/json"}
                        :body    big-json}))
      (on-receive channel update-status)
      ;; On channel close just remove it from the subscribed channels
      (on-close channel (fn [status]
                          (log/info "Channel closed.")
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
  (log/info "Starting server on 8080...")
  ;; wrap-defaults is middleware magic: takes requests and routes them
  (run-server (wrap-defaults all-routes api-defaults)
              {:ip "0.0.0.0"
               :port 8080}))
