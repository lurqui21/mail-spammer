package domain

import akka.actor.{ActorRef, Cancellable}
import org.joda.time.DateTime
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.mailer.{Attachment, AttachmentFile}
import play.api.mvc.MultipartFormData

import scala.util.Try

object formats {
  implicit val mailerConfFormat = Json.format[MailerConf]
  implicit val sendEmailFormat = Json.format[SendEmail]
  implicit val jobRequestFormat = Json.format[JobRequest]
}


case class MailerConf(host: String, port: Int, username: String, password: String)

case class SendEmail(conf: MailerConf,
                     from: String,
                     to: List[String],
                     subject: String,
                     text: String)

case class JobRequest(sendEmail: SendEmail,
                      frequency: Int, // in msg/min
                      duration: Int) // in min

case class JobData(created: DateTime, jobTask: Cancellable, stopTask: Cancellable, jobReq: JobRequest)

case class Job(jobId: Int, actor: ActorRef,jobTask: Cancellable, stopTask: Cancellable)

case class RichEmail(sendEmail: SendEmail, attachments: List[Attachment])
object RichEmail {
  import formats._

  def parse(form: MultipartFormData[TemporaryFile]): Try[RichEmail] = Try {
    val json = form.asFormUrlEncoded("email").headOption.getOrElse("{}")
    val files = form.files.map(file => AttachmentFile(file.filename, file.ref.file))
    val sendEmail = Json.parse(json).as[SendEmail]
    RichEmail(
      sendEmail, files.toList
    )
  }
}