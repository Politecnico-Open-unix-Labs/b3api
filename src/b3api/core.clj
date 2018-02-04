(ns b3api.core
  (:require [org.httpkit.server :refer :all]
            [taoensso.timbre :as timbre
             :refer (log trace debug info warn error fatal report spy)]
            [taoensso.timbre.appenders.core :as appenders]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [compojure.route :refer [files not-found]]
            [compojure.handler :refer [site]] ; form, query params decode; cookie; session, etc
            [compojure.core :refer [defroutes GET POST DELETE ANY context]]
            [taoensso.encore :as enc :refer [if-lets update-in* reset-in!]]
            [cheshire.core :refer [generate-string parse-string]])
  (:gen-class))


(defonce channel-list (atom [])) ;; The list of open websockets
(defonce status       (atom {})) ;; The actual status, once read from the json
(defonce tokens       (atom {})) ;; Map of tokens:authorized-path
(defonce o            (Object.)) ;; The global lock object


;; Logging config
(def log-file-name  "./data/b3api.log")
(def hist-file-name "./data/history.log")
;; One standard logger, and one for history
;; HACK: REPORT level (the higher) is reserved for history, don't use for other.
;; It gets printed only in the history logger, while ignored in the others.
(timbre/merge-config!
 {:appenders
  {:spit    (assoc
              (appenders/spit-appender {:fname log-file-name})
              :fn (fn [data]
                    (when-not (= (:level data) :report)
                      ((:fn (appenders/spit-appender {:fname log-file-name}))
                       data))))
   :println (assoc
              (appenders/println-appender)
              :fn (fn [data]
                    (when-not (= (:level data) :report)
                      ((:fn (appenders/println-appender)) data))))
   :history (assoc
              (appenders/spit-appender {:fname hist-file-name})
              :min-level :report)}})
;; No colors plz, and better (sortable!) timestamps
(timbre/merge-config! {:output-fn (partial timbre/default-output-fn
                                           {:stacktrace-fonts {}})
                       :timestamp-opts {:pattern "yyyy-MM-dd HH:mm:ss"}})


(def req-headers
  { "Access-Control-Allow-Origin" "*"
    "Access-Control-Allow-Headers" "Content-Type"
    "Access-Control-Allow-Methods" "GET,POST,OPTIONS"
    "Content-Type" "application/json; charset=utf-8"})


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


(defn read-json
  "Reads the json from the data folder, returns a clojure data structure"
  [filename]
  (let [f (str fs/*cwd* "/data/" filename ".json")]
    (when (not (fs/exists? f))
      (spit f "{}"))
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
         (info "New update: " new-msg)
         ;; Broadcasting only the authorized part
         (broadcast! (generate-string new-msg))
         ;; Append to history log:
         (report new-msg)
         ;; TODO: append to a in-memory circular-buffer with the last N history logs
         ;; Deep merging the authorized branch of the maps tree, and writing to file
         (reset-in! status auth-path (deep-merge* old-data new-data))
         (write-status-json!))
       (warn "Not authenticated!")))))


(defn update-handler
  "Handle POST client messages"
  [ring-request]
  (let [data (slurp (:body ring-request))] ;; slurp because it is a stream
    (info data)
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
          (swap! channel-list conj channel)
          (info "New websocket client. #connected:" (count @channel-list))
          (send! channel big-json false))
        ;; If HTTP just send down the json
        (do
          (info "GET from" (:remote-addr ring-request))
          (send! channel {:status  200
                          :headers req-headers
                          :body    big-json})))
      (on-receive channel update-status)
      ;; On channel close just remove it from the subscribed channels
      (on-close
       channel
       (fn [status]
         (when (websocket? channel)
           (reset! channel-list (remove #(= % channel) @channel-list))
           (info "Client disconnected. #connected:" (count @channel-list))))))))

(defroutes all-routes
  (GET  "/" [] root-handler)     ;; Websocket + GET
  (POST "/" [] update-handler)   ;; POST for one-time updates or fallback
  (ANY  "*" [] hist-handler)     ;;
  (not-found   root-handler))    ;; Because fallback.

(defn -main
  "Load server data, start server."
  [& args]
  ;; Read jsons from file
  (info "Reading tokens and status dump from disk...")
  (when-not (fs/exists? "./data") (fs/mkdir "./data"))
  (reset! status (read-json "status"))
  (reset! tokens (read-json "tokens"))
  (info "Starting server on 8080...")
  ;; wrap-defaults is middleware magic: takes requests and routes them
  (run-server (wrap-defaults all-routes api-defaults)
              {:ip "0.0.0.0"
               :port 8080}))
