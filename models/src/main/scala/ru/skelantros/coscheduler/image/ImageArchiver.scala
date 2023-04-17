package ru.skelantros.coscheduler.image

import cats.effect.{Resource, Sync}

import java.io.File
import java.nio.file.Files
import scala.util.{Failure, Success, Try}

object ImageArchiver {

    /**
     * Возвращает архив образа в виде ресурса, если папка imageDir представляет собой образ Docker:
     * imageDir - папка с файлом Dockerfile.
     * Архив после использования удаляется.
     */
    def apply[F[_]: Sync](imageDir: File, tarName: String): Resource[F, ImageArchive] =
        Resource.make(Sync[F].fromTry(tryArchive(imageDir, tarName)))(
            archive => Sync[F].delay(Files.delete(archive.file.toPath))
        )

    private def containsDockerfile(imageDir: File) = new File(imageDir, "Dockerfile").isFile

    def tryArchive(imageDir: File, tarName: String): Try[ImageArchive] = {
        if (!imageDir.isDirectory || !containsDockerfile(imageDir))
            Failure(ImageArchiverException.IncorrectDirectory(imageDir))
        else {
            import scala.sys.process._
            val tarFile = new File(s"/tmp/$tarName.tar")
            val result = (s"""find $imageDir/ -printf "%P\n"""" #| s"""tar -czf $tarFile --no-recursion -C $imageDir/ -T -""").!
            if (result != 0) Failure(ImageArchiverException.TarException(imageDir, result))
            else Success(ImageArchive(tarFile))
        }
    }
}
