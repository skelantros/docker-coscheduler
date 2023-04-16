package ru.skelantros.coscheduler.image

import java.io.File
import scala.util.{Failure, Try, Success}

object ImageArchiver {
    private def containsDockerfile(imageDir: File) = new File(imageDir, "Dockerfile").isFile

    def apply(imageDir: File, tarName: String): Try[ImageArchive] = {
        if(!imageDir.isDirectory || !containsDockerfile(imageDir))
            Failure(ImageArchiverException.IncorrectDirectory(imageDir))
        else {
            import scala.sys.process._
            val tarFile = new File(s"/tmp/$tarName.tar")
            val result = (s"""find $imageDir/ -printf "%P\n"""" #| s"""tar -czf $tarFile --no-recursion -C $imageDir/ -T -""").!
            if(result != 0) Failure(ImageArchiverException.TarException(imageDir, result))
            else Success(ImageArchive(tarFile))
        }
    }
}
