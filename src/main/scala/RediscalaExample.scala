import com.typesafe.scalalogging.Logger

object RediscalaExample extends App{
    val logger = Logger(this.getClass)

    implicit val akkaSystem = akka.actor.ActorSystem()
}
