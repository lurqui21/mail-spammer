package actor

import actor.MailerDispatcher.{Cancel, Send, Stop}
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import domain.{Job, JobRequest, MailerConf}
import service.MailerClientFactory

import scala.collection.mutable
import scala.concurrent.duration._


class MailerDispatcher(mailerFactory: MailerClientFactory) extends Actor with ActorLogging {
  var counter = 0
  val jobs = mutable.Set.empty[Job]
  import context.dispatcher

  override def receive = {
    case req: JobRequest =>
      val interval = (60 * 1000 / req.frequency).millis
      counter += 1
      val jobId = counter
      val client = mailerClient(req.sendEmail.conf)
      val worker = context.actorOf(MailerWorker.props(jobId, req, client))

      val jobTask = context.system.scheduler.schedule(1.seconds, interval, worker, Send)
      val stopTask = context.system.scheduler.scheduleOnce(req.duration.minutes, worker, Cancel)

      jobs.add(Job(jobId, worker, jobTask, stopTask))

      sender() ! jobId

    case jobId: Int =>
      jobs.find(_.jobId == jobId).foreach { j =>
        j.jobTask.cancel()
        j.stopTask.cancel()
        j.actor ! Stop

        sender() ! jobId
    }

    case Terminated(worker) =>
      jobs.find(_.actor == worker).foreach { j =>
        log.info(s"Job ${j.jobId} is stopped")
      }
  }

  def mailerClient(conf: MailerConf) = mailerFactory.mailerClient(conf)
}

object MailerDispatcher {
  def props(mailerFactory: MailerClientFactory) = Props(new MailerDispatcher(mailerFactory))

  case class ActorJob(jobReq: JobRequest, actor: ActorRef)
  case object Send
  case object Stop
  case object Cancel
}
