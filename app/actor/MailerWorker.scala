package actor

import actor.MailerDispatcher.{Cancel, Send, Stop}
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import domain.JobRequest
import play.api.libs.mailer.{Email, MailerClient}

import scala.concurrent.Future
import scala.util.{Failure, Success}


class MailerWorker(jobId: Int, jobReq: JobRequest, client: MailerClient) extends Actor with ActorLogging {
  import context.dispatcher
  override def receive = {
    case Send =>
      val mailData = jobReq.sendEmail
      val email = Email(
        subject = mailData.subject,
        from = mailData.from,
        to = mailData.to,
        bodyText = Some(mailData.text)
      )
      Future(/*client.send(email)*/0).onComplete {
        case Success(respId) => log.debug(s"Sent message, jobId: $jobId, respId: $respId")
        case Failure(e) => log.error(s"Failed to sent email, jobId: $jobId", e)
      }

    case Cancel =>
      log.info(s"Stopping jobId: $jobId")
      sender() ! jobId

    case Stop =>
      log.info(s"Job $jobId is stopped")
      self ! PoisonPill
  }
}

object MailerWorker {
  def props(jobId: Int, jobRequest: JobRequest, client: MailerClient) = Props(new MailerWorker(jobId, jobRequest, client))
}
