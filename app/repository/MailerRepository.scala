package repository

import javax.inject.{Inject, Singleton}

import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import domain.MailerConf

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MailerRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import driver.api._

  private class MailerConfTable(tag: Tag) extends Table[MailerConf](tag, "mailer-conf") {

    def host = column[String]("host")
    def port = column[Int]("port")
    def username = column[String]("username", O.PrimaryKey)
    def password = column[String]("password")

    def * = (host, port, username, password) <> (MailerConf.tupled, MailerConf.unapply)
  }

  private val configs = TableQuery[MailerConfTable]

  def insert(conf: MailerConf): Future[Int] = db.run {
    configs += conf
  }

  def list(): Future[Seq[MailerConf]] = db.run {
    configs.result
  }

  def byUsername(username: String): Future[Option[MailerConf]] = db.run {
    configs.filter(x => x.username === username).result.headOption
  }
}
