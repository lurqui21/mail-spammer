package controller

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.{Inject, Singleton}
import domain.formats.sendEmailFormat
import domain.{MailerConf, RichEmail, SendEmail}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.mailer.Email
import play.api.libs.streams.ActorFlow
import play.api.mvc.{Action, Controller, WebSocket}
import service.MailerClientFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class StandaloneMailController @Inject()(mailerService: MailerClientFactory)
                                        (implicit ec: ExecutionContext,
                                         system: ActorSystem,
                                         materializer: Materializer) extends Controller {
  private val logger = Logger(getClass)

  def sendEmail = Action.async(parse.json[SendEmail]) { req =>
    val data = req.body
    val email = Email(
      subject = data.subject,
      from = data.from,
      to = data.to,
      bodyText = Some(data.text))
    processEmail(email, data.conf)
  }

  private def processEmail(email: Email, conf: MailerConf) = {
    val client = mailerService.mailerClient(conf)
    logger.info(s"Preparing to send $email")
    Future(client.send(email))
      .map { respId =>
        logger.info(s"Email $email is sent, messageId: $respId")
        Ok(respId)
      } recover {
      case e =>
        logger.error(s"Email $email is not sent", e)
        InternalServerError(s"Email is not sent ${e.getMessage}")
    }
  }

  def sendEmailWithAttachements = Action.async(parse.multipartFormData) { req =>
    RichEmail.parse(req.body) match {
      case Failure(e) => Future.successful(BadRequest(s"Could not parse request: ${e.getMessage}"))
      case Success(richEmail) =>
        val data = richEmail.sendEmail
        val email = Email(
          subject = data.subject,
          from = data.from,
          to = data.to,
          bodyText = Some(data.text),
          attachments = richEmail.attachments)
        processEmail(email, data.conf)
    }
  }
}
