import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.stream.Collectors
import java.util.stream.Stream

Stream<Path> paths
List<Path> astJarPaths
try {
    File f = new File(System.getenv('WORKSPACE'))
    paths = Files.walk(f.toPath())
    astJarPaths = paths.filter({p -> p.toFile().isFile() && p.toString().endsWith('-ast.jar')}).collect(Collectors.toList())
} finally {
    if (paths != null) {
        paths.close()
    }
}

String usernamePassword = System.getenv('NEXUS_CREDENTIALS')
def basicAuth = Base64.getEncoder().encodeToString(usernamePassword.getBytes('UTF-8'))

astJarPaths.each({astJarPath ->
    def scmProperties = loadScmProperties(astJarPath)
    def groupId = scmProperties.getProperty('groupId')
    def artifactId = scmProperties.getProperty('artifactId')
    def version = scmProperties.getProperty('version')
    def repositoryRootUrl = 'http://nexus:8081/repository/moderne-ast'
    def url = new URL("${repositoryRootUrl}/${groupId.replace('.','/')}/${artifactId}/${version}/${artifactId}-${version}-ast.jar")
    println "Publishing ${astJarPath} to ${url}"
    def connection = (HttpURLConnection) url.openConnection()
    connection.setRequestProperty('Authorization', "Basic ${basicAuth}")
    connection.setRequestMethod('PUT')
    connection.setDoOutput(true)
    def connectionOutputStream = connection.getOutputStream()
    def astJarInputStream
    try {
        astJarInputStream = Files.newInputStream(astJarPath)
        copy(astJarInputStream, connectionOutputStream)
        connectionOutputStream.flush()
        connectionOutputStream.close()
    } finally {
        if (astJarInputStream != null) {
            astJarInputStream.close()
        }
    }

    def responseCode = connection.getResponseCode()
    if (responseCode < 200  || responseCode > 299) {
        if (connection.getErrorStream() == null) {
            println "ERROR: Response code: ${responseCode}"
        } else {
            def errorBaos = new ByteArrayOutputStream()
            copy(connection.getErrorStream(), errorBaos)
            def errorMessage = new String(errorBaos.toByteArray(), StandardCharsets.UTF_8)
            println "ERROR: Response code: ${responseCode}, response body: ${errorMessage}"
        }
    }
})

static Properties loadScmProperties(Path astJarPath) {
    Properties scmProperties = new Properties()
    JarFile jarFile
    try {
        jarFile = new JarFile(astJarPath.toFile())
        JarEntry scmPropsEntry = jarFile.stream().filter({ e -> e.getName().equals('scm.properties') }).findFirst().orElse(null);
        if (scmPropsEntry == null) {
            throw new IllegalArgumentException("JAR ${astJarPath} did not contain scm.properties");
        }
        scmProperties.load(jarFile.getInputStream(scmPropsEntry))
        return scmProperties
    } finally {
        if (jarFile != null) {
            jarFile.close()
        }
    }
}

static int copy(InputStream inputStream, OutputStream out) throws IOException {
    def byteCount = 0
    byte[] buffer = new byte[4096]
    int bytesRead
    while ((bytesRead = inputStream.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead)
        byteCount += bytesRead
    }
    out.flush()
    return byteCount
}