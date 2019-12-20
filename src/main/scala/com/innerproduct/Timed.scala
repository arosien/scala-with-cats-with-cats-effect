package com.innerproduct

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import cats.effect.concurrent.Ref

/** Receives timing observation for some named action. */
trait Timed[F[_]] {
  def observe(name: String, duration: FiniteDuration): F[Unit]
}

object Timed {
  def apply[F[_]](implicit t: Timed[F]): Timed[F] = t

  /** Log timing information via a `Logger`. */
  def logger[F[_]: Sync: Logger]: Timed[F] =
    (name, duration) => Logger[F].trace(s"$name: ${duration.toMillis}")

  /** Update a [Timed.Log] when observing timing information. */
  def updateLog[F[_]](ref: Ref[F, Log]): Timed[F] =
    (name, duration) => ref.update(_.update(name, duration))

  case class Log(durations: Map[String, List[FiniteDuration]] = Map.empty) {
    def update(name: String, duration: FiniteDuration): Log =
      Log(durations |+| Map(name -> List(duration)))
  }

  object Log {
    def unsafeRef[F[_]: Sync]: Ref[F, Log] = Ref.unsafe(Log())

    def resource[F[_]: Sync]: Resource[F, Ref[F, Log]] =
      Resource.liftF(Ref.of(Log()))
  }

  /** Time an action and send timing observation to a [Timed] instance. */
  def observe[E, F[_]: Bracket[?[_], E]: Clock: Timed, A](
      name: String,
      unit: TimeUnit = TimeUnit.MICROSECONDS
  )(
      fa: F[A]
  ): F[A] =
    Bracket[F, E].bracket(Clock[F].monotonic(unit)) { _ =>
      fa
    } { start: Long =>
      Clock[F]
        .monotonic(unit)
        .flatMap(
          now => Timed[F].observe(name, FiniteDuration(now - start, unit))
        )
    }
}
