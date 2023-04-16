package ru.skelantros.coscheduler.image

import java.io.File

sealed class ImageArchiverException(msg: String) extends Exception(msg)

object ImageArchiverException {
    case class IncorrectDirectory(dir: File)
        extends ImageArchiverException(s"Cannot create image archive from $dir because it's not a directory that contains Dockerfile.")

    case class TarException(dir: File, exitCode: Int)
        extends ImageArchiverException(s"Cannot pack $dir to tar; exit code is $exitCode")
}