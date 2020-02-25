package unit.kafka.server

import java.util.Properties

import kafka.api.KAFKA_2_3_IV1
import kafka.server.KafkaConfig
import kafka.utils.TestUtils
import kafka.zk.ZooKeeperTestHarness
import org.apache.kafka.common.TopicPartition
import org.junit.Test

class CacheableBrokerEpochIntegrationTest extends ZooKeeperTestHarness {
  val brokerId = 0
  val controllerId = 1

  @Test
  def testNewControllerConfig(): Unit = {
    testControlRequests(true)
  }

  @Test
  def testOldControllerConfig(): Unit = {
    testControlRequests(false)
  }

  def testControlRequests(controllerUseNewConfig: Boolean): Unit = {
    val controllerConfig: Properties =
      if (controllerUseNewConfig) {
        TestUtils.createBrokerConfig(controllerId, zkConnect)
      } else {
        val oldConfig = TestUtils.createBrokerConfig(controllerId, zkConnect)
        oldConfig.put(KafkaConfig.InterBrokerProtocolVersionProp, KAFKA_2_3_IV1.toString)
        oldConfig
      }
    val controller = TestUtils.createServer(KafkaConfig.fromProps(controllerConfig))

    // Note that broker side logic does not depend on the config
    val brokerConfig = TestUtils.createBrokerConfig(brokerId, zkConnect)
    val broker = TestUtils.createServer(KafkaConfig.fromProps(brokerConfig))
    val servers = Seq(controller, broker)

    val tp = new TopicPartition("new-topic", 0)

    try {
      // Use topic creation to test the LeaderAndIsr and UpdateMetadata requests
      TestUtils.createTopic(zkClient, tp.topic(), partitionReplicaAssignment = Map(0 -> Seq(brokerId, controllerId)),
        servers = servers)
      TestUtils.waitUntilLeaderIsKnown(Seq(broker), tp, 10000)
      TestUtils.waitUntilMetadataIsPropagated(Seq(broker), tp.topic(), tp.partition())

      // Use topic deletion to test StopReplica requests
      adminZkClient.deleteTopic(tp.topic())
      TestUtils.verifyTopicDeletion(zkClient, tp.topic(), 1, servers)
    } finally {
      TestUtils.shutdownServers(servers)
    }
  }
}
