(ns kafka.test.zk
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [kafka.test.fs :as fs])
  (:import
   (java.net InetSocketAddress)
   (kafka.utils ZkUtils)
   (org.apache.zookeeper.server ServerCnxnFactory
                                ZooKeeperServer)
   (org.apache.zookeeper KeeperException$NoNodeException)
   (org.I0Itec.zkclient ZkClient
                        ZkConnection)))

(def zk-connect "zookeeper.connect")
(def zk-session-timeout "zookeeper.session.timeout.ms")
(def zk-connection-timeout "zookeeper.connection.timeout.ms")

(defn- int-get
  [config key default]
  (try
    (Integer/parseInt (get config key default))
    (catch Exception e
      (let [msg (format "Invalid config value '%s' for key '%s'"
                        (get config key default)
                        key)
            context {:config config
                       :key key
                       :default default}]
        (throw (ex-info msg context e))))))

(defn- ensure-connect-string
  [config]
  (when-not (get config zk-connect)
    (throw (ex-info "Zookeeper connection info not set"
                    config))))

(defn port
  "Parse the zookeeper port out of a kafka server config"
  [connect-string]
  (-> connect-string
      (str/split #":")
      (nth 1)
      read-string))

(defn client
  [config]
  (ensure-connect-string config)

  (let [connect         (get config zk-connect)
        connect-timeout (int-get config zk-connection-timeout "1000")
        session-timeout (int-get config zk-session-timeout "5000")]
    (ZkUtils/createZkClient connect connect-timeout session-timeout)))

(defn utils
  [zk-client]
  (ZkUtils/apply zk-client false))

(defn start! [{:keys [config snapshot-dir log-dir]}]
  (let [tick-time 500
        zk           (ZooKeeperServer. (io/file snapshot-dir)
                                       (io/file log-dir)
                                       tick-time)
        factory      (doto (ServerCnxnFactory/createFactory)
                       (.configure (-> (port (get config "zookeeper.connect"))
                                       (InetSocketAddress.)) 0))]

    (.startup factory zk)

    {:zk zk
     :factory factory}))


(defn stop! [{:keys [zk config factory snapshot-dir log-dir]}]
  (try
    (-> (org.apache.zookeeper.jmx.MBeanRegistry/getInstance)
        (.unregisterAll))
    (when factory
      (.shutdown factory))

    {:zk nil
     :factory nil}

    (finally
      (mapv fs/try-delete! [snapshot-dir log-dir]))))