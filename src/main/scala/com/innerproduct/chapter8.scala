package com.innerproduct

import cats._
import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration._
import scala.util.Random

/*
  Scala with Cats, Chapter 8:
  Case Study: Testing Asynchronous Code
  https://books.underscore.io/scala-with-cats/scala-with-cats.html#sec:case-studies:testing
*/

trait UptimeClient[F[_]] {
  def getUptime(hostname: String): F[Int]
}

object UptimeClient {
  def test(hosts: Map[String, Int]): UptimeClient[Id] =
    hostname => hosts.getOrElse(hostname, 0)

  /** IO-bound actions should always be run on a `Blocker`. */
  def blocking[F[_]: Sync: ContextShift: Logger: Timed: Clock](
      blocker: Blocker
  ): UptimeClient[F] =
    hostname =>
      blocker.blockOn {
        Timed.observe(s"uptime/$hostname") {
          for {
            uptime <- Sync[F].delay {
              val i = Random.nextInt(100)
              Thread.sleep(i)
              i
            }
            _ <- Logger[F].info(s"$hostname uptime: $uptime")
          } yield uptime
        }
      }
}

trait UptimeService[F[_]] {
  def getTotalUptime(hostnames: List[String]): F[Int]
}

object UptimeService {
  /** Sequential execution of uptime per host. */
  def seq[F[_]: Monad](client: UptimeClient[F]): UptimeService[F] =
    hostnames =>
      hostnames
        .traverse(client.getUptime)
        .map(_.sum)

  /** Parallel execution of uptime per host. */
  def par[F[_]: Monad: Parallel](client: UptimeClient[F]): UptimeService[F] =
    hostnames =>
      hostnames
        .parTraverse(client.getUptime)
        .map(_.sum)
}

/** Demonstrate blocking IO happening on designated blocker threads, vs.
  * "normal" computations happening on computation threads.
  *
  * {{{
  * [ioapp-compute-1] INFO  c.i.UptimeMain - Tick: 0
  * [cats-effect-blocker] INFO  c.i.UptimeMain - h3 uptime: 30
  * [cats-effect-blocker] INFO  c.i.UptimeMain - h1 uptime: 69
  * [cats-effect-blocker] INFO  c.i.UptimeMain - h2 uptime: 76
  * [cats-effect-blocker] INFO  c.i.UptimeMain - h4 uptime: 94
  * [ioapp-compute-3] INFO  c.i.UptimeMain - Total uptime: 269
  * [ioapp-compute-4] INFO  c.i.UptimeMain - Tick: 1
  * [cats-effect-blocker] INFO  c.i.UptimeMain - h2 uptime: 19
  * [cats-effect-blocker] INFO  c.i.UptimeMain - h3 uptime: 48
  * [cats-effect-blocker] INFO  c.i.UptimeMain - h4 uptime: 70
  * [cats-effect-blocker] INFO  c.i.UptimeMain - h1 uptime: 92
  * [ioapp-compute-5] INFO  c.i.UptimeMain - Total uptime: 229
  * ...
  * }}}
  */
object UptimeMain extends IOApp {
  implicit def unsafeLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
  val log = Timed.Log.unsafeRef[IO]
  implicit val timed = Timed.updateLog(log)

  def run(args: List[String]): IO[ExitCode] =
    resources
      .use(service => Loop(action(service, hostnames), 10))
      .guaranteeCase {
        case ExitCase.Canceled =>
          Logger[IO].warn("Interrupted: releasing and exiting!")
        case _ =>
          Logger[IO].warn("Normal exit!")
      }

  val resources: Resource[IO, UptimeService[IO]] =
    for {
      blocker <- Blocker[IO]
    } yield UptimeService.par(UptimeClient.blocking(blocker))

  def action(service: UptimeService[IO], hostnames: List[String]): IO[Unit] =
    for {
      total <- Timed.observe("total")(service.getTotalUptime(hostnames))
      ds <- log.get
      _ <- Logger[IO].info(s"Total uptime: $total")
      _ <- Logger[IO].trace(s"Durations: $ds")
    } yield ()
  val hostnames = List("h1", "h2", "h3", "h4")
}

object Loop {

  /** Loop over some action. */
  def apply[F[_]: Sync: Logger: Timer](
      action: F[Unit],
      numTimes: Int
  ): F[ExitCode] = {
    def doLoop(action: F[Unit], n: Int): F[ExitCode] =
      Sync[F].suspend {
        if (n < numTimes)
          Timer[F].sleep(1.second) *> Logger[F].info(s"Tick: $n") *> action *> doLoop(
            action,
            n + 1
          )
        else Sync[F].pure(ExitCode.Success)
      }

    doLoop(action, 0)
  }
}
