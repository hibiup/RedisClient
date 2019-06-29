package rediscala
import akka.util.ByteString
import redis.{RedisBlockingClient, RedisClient}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PingPong extends App{
    implicit val akkaSystem = akka.actor.ActorSystem()

    val redis = RedisClient("192.168.56.101", 6379)

    val futurePong = redis.ping()
    akkaSystem.log.info("Ping sent!")

    futurePong.map(pong => {
        akkaSystem.log.info(s"Redis replied with a $pong")
    })

    Await.result(futurePong, 5 seconds)

    akkaSystem.terminate()
    akkaSystem.whenTerminated
}


/**
  * 读写两条线程
  * */
object WriteReader extends App {
    implicit val akkaSystem = akka.actor.ActorSystem()

    val redisSender = RedisClient("192.168.56.101", 6379)
    val redisReader = RedisClient("192.168.56.101", 6379)

    /** 发布新信息之前删除旧的记录 */
    val r = redisSender.del("workList").flatMap(_ => {
        val c = consumer()
        publisher()
        c
    })
    Await.result(r, Duration.Inf)

    akkaSystem.terminate()
    akkaSystem.whenTerminated

    /**
      * 定义一个发布端
      * */
    def publisher() = {
        /**
          * 从头部依次压入 3 条元素，得到存储顺序：
          *   doSomeWork_3, doSomeWork_2, doSomeWork_1
          * */
        redisSender.lpush("workList", "doSomeWork_1", "doSomeWork_2", "doSomeWork_3")
    }

    /**
      * 定义一个消费端
      * */
    def consumer(): Future[IndexedSeq[Unit]] = {
        val waitWork = 3   // 一共 3 条数据
        val sequenceFuture: IndexedSeq[Future[Unit]] = for {_ <- 0 until waitWork } yield
            /**
              * 从右到左弹出，也就是说：
              *   workList: doSomeWork_3, doSomeWork_2, doSomeWork_1 => doSomeWork_1, doSomeWork_2, doSomeWork_3
              **/
            redisReader.rpop("workList").map((result: Option[ByteString]) =>
                result.foreach{ work: ByteString => println(s"""Pop: ${work.utf8String}""") }
            )

        Future.sequence(sequenceFuture)
    }
}

/**
  * RedisBlockingClient 提供阻塞操作，但是它可以一次读取多条 key 的结果
  * */
object BlockClient extends App {
    implicit val akkaSystem = akka.actor.ActorSystem()

    val redisSender = RedisClient("192.168.56.101", 6379)
    val redisReader = RedisBlockingClient("192.168.56.101", 6379)

    /** 发布新信息之前删除旧的记录 */
    val r = redisSender.del("workList", "otherWorkList").flatMap(_ => {
        val c = consumer()
        publisher()
        c
    })
    Await.result(r, Duration.Inf)

    akkaSystem.terminate()
    akkaSystem.whenTerminated

    /**
      * 定义一个发布端
      * */
    def publisher() = {
        /**
          * 从头部依次压入 2 条元素：
          *   doSomeWork_2, doSomeWork_1
          * */
        redisSender.lpush("workList", "doSomeWork_1", "doSomeWork_2")

        /**
          * 从右边依次压入 3 条字符串元素：
          *   doOtherWork_1, doOtherWork_2, doOtherWork_3
          * */
        redisSender.rpush("otherWorkList", "doOtherWork_1", "doOtherWork_2", "doOtherWork_3")
    }

    /**
      * 定义一个消费端
      * */
    def consumer(): Future[IndexedSeq[Unit]] = {
        val waitWork = 5   // 一共 5 条数据
        val sequenceFuture: IndexedSeq[Future[Unit]] = for {_ <- 0 until waitWork } yield
            /**
              * 从右到左弹出，也就是说：
              *   workList: doSomeWork_2, doSomeWork_1 => doSomeWork_1, doSomeWork_2
              *   otherWorkList: doOtherWork_1, doOtherWork_2, doOtherWork_3 => doOtherWork_3, doOtherWork_2, doOtherWork_1
              **/
            redisReader.brpop(Seq("workList", "otherWorkList"), 5 seconds).map((result: Option[(String, ByteString)]) =>
                result.foreach{
                    case (key, work) => println(s"""$key: ${work.utf8String}""")
                }
            )

        Future.sequence(sequenceFuture)
    }
}

/**
  * Transaction 的想法是在 redis 连接之外定义事务。我们使用 TransactionBuilder 来存储将要对 redis 命令的调用
  * （对于每个命令，我们都会返回一个 Future）。最后调用 exec 时，TransactionBuilder 将构建所有命令并将其一起发送
  * 到服务器。通过这样做，我们可以使用与流水线的正常连接，并避免在事务中从外部捕获命令
  * */
object Transaction extends App {

    implicit val system = akka.actor.ActorSystem()

    val redis = RedisClient("192.168.56.101", 6379)
    redis.del("key")

    val redisTransaction = redis.transaction() // new TransactionBuilder
    redisTransaction.watch("key")
    val set = redisTransaction.set("key", "abcValue")
    val decr = redisTransaction.decr("key")
    val get = redisTransaction.get("key")
    redisTransaction.exec()

    /** desr 会失败 */
    decr.onComplete(error => system.log.error(s"decr failed : $error"))

    /** set 和 get 会成功 */
    val r = for {
        s <- set
        g <- get
    } yield {
        assert(s)  // True
        assert(g.contains(ByteString("abcValue")))  // True
    }
    Await.result(r, Duration.Inf)

    system.terminate()
    system.whenTerminated
}