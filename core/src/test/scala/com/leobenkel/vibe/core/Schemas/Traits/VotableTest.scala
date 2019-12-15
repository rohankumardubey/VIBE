package com.leobenkel.vibe.core.Schemas.Traits

import com.leobenkel.vibe.core.Schemas._
import com.leobenkel.vibe.core.TestUtils.TestRuntime
import org.scalatest.{Assertion, FreeSpec}
import zio.ZIO
import zio.clock.Clock
import zio.console.Console
import zio.random.Random

class VotableTest extends FreeSpec {
  "VotableTest" - {
    val runtime = TestRuntime()
    "Make User vote on Idea" - {
      def makeUser: ZIO[Any with Clock with Random with Console, Nothing, User] =
        for {
          designer  <- Skill("design")
          userSkill <- Skill("user")
          user <- User(
            name = "userName",
            email = "email",
            oauthToken = "abcToken",
            skills = Set(designer, userSkill)
          )
          _ <- ZIO.accessM[Console](_.console.putStrLn(s"Created user: $user"))
        } yield {
          user
        }

      val author: User = runtime
        .unsafeRunSync(makeUser).map(Some(_)).getOrElse(_ => None).get

      val user: User = runtime
        .unsafeRunSync(makeUser).map(Some(_)).getOrElse(_ => None).get

      val idea = for {
        t1 <- Tag("tag1")
        t2 <- Tag("tag2")
        idea <- Idea(
          title = "good idea",
          description = "this is awesome",
          author = author,
          tagsIds = Set(t1, t2)
        )
        _ <- ZIO.accessM[Console](_.console.putStrLn(s"Created idea: $idea"))
      } yield {
        idea
      }

      "Voting" in {
        val process: ZIO[TestRuntime.ENV, Throwable, Assertion] = for {
          i             <- idea
          originalVotes <- i.votes
          _             <- ZIO.accessM[Console](_.console.putStrLn(s"Read $originalVotes"))
          newIdea       <- i.voteUpBy(user)
          originalScore <- i.score
          newVotes      <- newIdea.votes
          newScore      <- newIdea.score
        } yield {
          assertResult(1, originalVotes)(originalScore)
          assertResult(2, newVotes)(newScore)
        }

        runtime.unsafeRunSync(process).getOrElse(c => throw c.squash)
      }
    }
  }
}
