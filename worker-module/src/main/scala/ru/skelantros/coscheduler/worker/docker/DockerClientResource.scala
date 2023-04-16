package ru.skelantros.coscheduler.worker.docker

import cats.effect.{IO, Resource}
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}

object DockerClientResource {
    def resource: Resource[IO, DefaultDockerClient] = {
        Resource.make(IO(DefaultDockerClient.fromEnv.build()))(client => IO(client.close()))
    }

    def apply[A](f: DockerClient => A): IO[A] =
        resource.use(client => IO(f(client)))
}
