package com.archinamon.api.jars

import com.android.SdkConstants
import com.android.utils.PathUtils
import com.google.common.collect.ImmutableSortedMap
import java.io.*
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.function.Predicate
import java.util.jar.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** Jar Merger class.  */
class JarMerger @Throws(IOException::class) @JvmOverloads constructor(
        jarFile: Path,
        private val filter: Predicate<String>? = null
) : Closeable {

    private val buffer = ByteArray(8192)

    private val jarOutputStream: JarOutputStream

    init {
        Files.createDirectories(jarFile.parent)
        jarOutputStream = JarOutputStream(BufferedOutputStream(Files.newOutputStream(jarFile)))
    }

    @Throws(IOException::class)
    @JvmOverloads
    fun addDirectory(
            directory: Path,
            filterOverride: Predicate<String>? = filter,
            transformer: Transformer? = null,
            relocator: Relocator? = null) {
        val candidateFiles = ImmutableSortedMap.naturalOrder<String, Path>()
        Files.walkFileTree(
                directory,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        var entryPath = PathUtils.toSystemIndependentPath(directory.relativize(file))
                        if (filterOverride != null && !filterOverride.test(entryPath)) {
                            return FileVisitResult.CONTINUE
                        }

                        if (relocator != null) {
                            entryPath = relocator.relocate(entryPath)
                        }

                        candidateFiles.put(entryPath, file)
                        return FileVisitResult.CONTINUE
                    }
                })
        val sortedFiles = candidateFiles.build()
        for ((entryPath, value) in sortedFiles) {
            BufferedInputStream(Files.newInputStream(value)).use { `is` ->
                if (transformer != null) {
                    val is2 = transformer.filter(entryPath, `is`)
                    if (is2 != null) {
                        write(JarEntry(entryPath), is2)
                    }
                } else {
                    write(JarEntry(entryPath), `is`)
                }
            }
        }
    }

    @Throws(IOException::class)
    @JvmOverloads
    fun addJar(
            file: Path,
            filterOverride: Predicate<String>? = filter,
            relocator: Relocator? = null) {
        ZipInputStream(BufferedInputStream(Files.newInputStream(file))).use { zis ->

            // loop on the entries of the jar file package and put them in the final jar
            while (true) {
                val entry = zis.nextEntry
                        ?: break

                // do not take directories
                if (entry.isDirectory) {
                    continue
                }

                // Filter out files, e.g. META-INF folder, not classes.
                var name = entry.name
                if (filterOverride != null && !filterOverride.test(name)) {
                    continue
                }

                if (relocator != null) {
                    name = relocator.relocate(name)
                }

                val newEntry = JarEntry(name)
                newEntry.method = entry.method
                if (newEntry.method == ZipEntry.STORED) {
                    newEntry.size = entry.size
                    newEntry.compressedSize = entry.compressedSize
                    newEntry.crc = entry.crc
                }
                newEntry.lastModifiedTime = FileTime.fromMillis(0)

                // read the content of the entry from the input stream, and write it into the
                // archive.
                write(newEntry, zis)
            }
        }
    }

    @Throws(IOException::class)
    fun addFile(entryPath: String, file: Path) {
        BufferedInputStream(Files.newInputStream(file)).use { `is` -> write(JarEntry(entryPath), `is`) }
    }

    @Throws(IOException::class)
    fun addEntry(entryPath: String, input: InputStream) {
        BufferedInputStream(input).use { `is` -> write(JarEntry(entryPath), `is`) }
    }

    @Throws(IOException::class)
    override fun close() {
        jarOutputStream.close()
    }

    @Throws(IOException::class)
    fun setManifestProperties(properties: Map<String, String>) {
        val manifest = Manifest()
        val global = manifest.mainAttributes
        global[Attributes.Name.MANIFEST_VERSION] = "1.0.0"
        properties.forEach(
                { attributeName, attributeValue -> global[Attributes.Name(attributeName)] = attributeValue })
        val manifestEntry = JarEntry(JarFile.MANIFEST_NAME)
        setEntryAttributes(manifestEntry)
        jarOutputStream.putNextEntry(manifestEntry)
        try {
            manifest.write(jarOutputStream)
        } finally {
            jarOutputStream.closeEntry()
        }
    }

    @Throws(IOException::class)
    private fun write(entry: JarEntry, from: InputStream) {
        setEntryAttributes(entry)
        jarOutputStream.putNextEntry(entry)
        while (true) {
            val count = from.read(buffer)
            if (count == -1)
                break

            jarOutputStream.write(buffer, 0, count)
        }

        jarOutputStream.closeEntry()
    }

    private fun setEntryAttributes(entry: JarEntry) {
        entry.lastModifiedTime = fileTime
        entry.lastAccessTime = fileTime
        entry.creationTime = fileTime
    }

    interface Transformer {
        /**
         * Transforms the given file.
         *
         * @param entryPath the path within the jar file
         * @param input an input stream of the contents of the file
         * @return a new input stream if the file is transformed in some way, the same input stream
         * if the file is to be kept as is and null if the file should not be packaged.
         */
        fun filter(entryPath: String, input: InputStream): InputStream?
    }

    interface Relocator {
        fun relocate(entryPath: String): String
    }

    companion object {

        val CLASSES_ONLY = { archivePath: String -> archivePath.endsWith(SdkConstants.DOT_CLASS) }
        val EXCLUDE_CLASSES = { archivePath: String -> !archivePath.endsWith(SdkConstants.DOT_CLASS) }

        val fileTime: FileTime = FileTime.fromMillis(0)
    }
}
