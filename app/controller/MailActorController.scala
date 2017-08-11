package controller

import actor.MailerDispatcher
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.{Inject, Singleton}
import domain.JobRequest
import domain.formats.jobRequestFormat
import play.api.Logger
import play.api.mvc.{Action, Controller}
import service.MailerClientFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class MailActorController @Inject()(mailerFactory: MailerClientFactory,
                                    system: ActorSystem)
                                   (implicit ec: ExecutionContext) extends Controller {
  private val logger = Logger(getClass)
  implicit val timeout = Timeout(5.seconds)
  val mailer: ActorRef = system.actorOf(MailerDispatcher.props(mailerFactory))

  def startJob = Action.async(parse.json[JobRequest]) { req =>
    (mailer ? req.body)
      .mapTo[Int]
      .map(jobId => Ok(jobId.toString))
  }

  def stopJob(jobId: Int) = Action.async {
    (mailer ? jobId).map(jobId => Ok(jobId.toString))
  }
}
