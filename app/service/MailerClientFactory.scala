package service

import com.google.inject.{ImplementedBy, Singleton}
import domain.MailerConf
import play.api.libs.mailer.{MailerClient, SMTPConfiguration, SMTPMailer}

import scala.concurrent.Future

@ImplementedBy(classOf[SMPTMailerClientFactory])
trait MailerClientFactory {
  def mailerClient(conf: MailerConf): MailerClient
  def testCredentials(conf: MailerConf): Future[Boolean]
}

@Singleton
class SMPTMailerClientFactory extends MailerClientFactory {
  def mailerClient(conf: MailerConf): MailerClient = {
    new SMTPMailer(makeSmptConf(conf))
  }

  def testCredentials(conf: MailerConf): Future[Boolean] = ???

  private def makeSmptConf(conf: MailerConf) =
    SMTPConfiguration(conf.host, conf.port, ssl = true, user = Some(conf.username), password = Some(conf.password))
}
