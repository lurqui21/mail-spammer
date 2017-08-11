package controller

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import domain.formats.jobRequestFormat
import domain.{JobData, JobRequest}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.mailer.Email
import play.api.mvc.{Action, Controller}
import repository.MailerRepository
import service.MailerClientFactory

import scala.collection.convert.decorateAsScala._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class MailJobController @Inject()(repo: MailerRepository,
                                  mailerService: MailerClientFactory, system: ActorSystem)
                                 (implicit ec: ExecutionContext) extends Controller {
  val counter = new AtomicInteger()
  val jobs: mutable.Map[Int, JobData] = new ConcurrentHashMap[Int, JobData]().asScala
  private val logger = Logger(getClass)

  def startJob = Action(parse.json[JobRequest]) { req =>
    val jobReq = req.body
    val mailData = jobReq.sendEmail
    val client = mailerService.mailerClient(mailData.conf)
    val email = Email(
      subject = mailData.subject,
      from = mailData.from,
      to = mailData.to,
      bodyText = Some(mailData.text)
    )
    val interval = (60 * 1000 / jobReq.frequency).millis
    val jobId = counter.incrementAndGet()

    val jobTask = system.scheduler.schedule(1.seconds, interval) {
      Future(/*client.send(email)*/0).onComplete {
        case Success(respId) => logger.debug(s"Sent message, jobId: $jobId, respId: $respId")
        case Failure(e) => logger.error(s"Failed to sent email, jobId: $jobId", e)
      }
    }

    val stopTask = system.scheduler.scheduleOnce(jobReq.duration.minutes)(stopJob(jobId, false))

    jobs.put(jobId, JobData(new DateTime, jobTask, stopTask, jobReq))

    repo.insert(mailData.conf)

    Ok(jobId.toString)
  }

  def finishJob(jobId: Int) = Action {
    stopJob(jobId, true)

    Ok(jobId.toString)
  }

  private def stopJob(jobId: Int, manual: Boolean) = {
    jobs.get(jobId) match {
      case Some(job) if manual =>
        logger.info(s"Stopping job $jobId due to stop request")
        job.jobTask.cancel()
        job.stopTask.cancel()
      case Some(job) =>
        logger.debug(s"Requesting to stop job $jobId since duration is exceeded")
        job.jobTask.cancel()
      case None =>
        logger.info(s"Could not stop job $jobId. Job is not found")
    }
    jobs.remove(jobId)
  }
}
