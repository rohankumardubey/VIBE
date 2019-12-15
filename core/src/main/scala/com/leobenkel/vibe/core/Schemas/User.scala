package com.leobenkel.vibe.core.Schemas

import com.leobenkel.vibe.core.Schemas.Traits.SchemaBase._
import com.leobenkel.vibe.core.Schemas.Traits.TableRef.TABLE_NAME
import com.leobenkel.vibe.core.Schemas.Traits.{SchemaBase, TableRef}
import com.leobenkel.vibe.core.Schemas.User.OAuth
import com.leobenkel.vibe.core.Utils.IdGenerator
import zio.ZIO
import zio.clock.Clock
import zio.random.Random

case class User(
  id:                ID,
  creationTimestamp: Date,
  updateTimestamp:   Date,
  name:              String,
  email:             String,
  oauthToken:        OAuth,
  jobTitle:          JobTitle.PK
) extends SchemaBase[User, ID] {
  @transient lazy val allIdeas: QueryZIO[Seq[Idea]] =
    Idea.querySpecific(s"${Idea.getTableName}.author = ${this.id}")

  @transient lazy val allEnrolled: QueryZIO[Seq[Idea]] =
    Idea.querySpecific(s"contains(${Idea.getTableName}.enrolled, ${this.id})")

  override def copy(updateTimestamp: Date): User = {
    this.copy(updateTimestamp = updateTimestamp)
  }
}

object User extends TableRef[ID, User] {
  type OAuth = String

  override def getTableName: TABLE_NAME = "users"

  override def queryOne(id: ID): QueryZIO[Option[User]] = ???

  override def querySeveral(id: Set[ID]): QueryZIO[Seq[User]] = ???

  override def querySpecific(whereClause: WHERE_CLAUSE[User]): QueryZIO[Seq[User]] = ???

  override def deleteRow(id: ID): QueryZIO[Boolean] = ???

  override def insert(row: User): QueryZIO[Boolean] = ???

  def apply(
    name:       String,
    email:      String,
    oauthToken: OAuth,
    jobTitle:   JobTitle
  ): ZIO[Any with Clock with Random, Nothing, User] =
    IdGenerator.generateId((name, email, oauthToken, jobTitle.id)).map {
      case (id, date) =>
        User(id, date, date, name, email, oauthToken, jobTitle.id)
    }

}
