package com.devmate.project.service;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.devmate.project.dto.PomCandidatesResponse;
import com.devmate.project.dto.ProjectInspectionResponse;
import com.devmate.project.model.ProjectRecord;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

@Service
public class ProjectDetector {

    private static final int MAX_POM_BYTES = 1024 * 1024;
    private static final int MAX_DISCOVERY_DEPTH = 4;
    private static final int MAX_DISCOVERY_ENTRIES = 5000;
    private static final int MAX_POM_CANDIDATES = 100;
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", ".idea", "target", "node_modules", "build", "dist");

    public PomCandidatesResponse findPomCandidates(ProjectRecord project) {
        Path root = Path.of(project.rootPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(root)) {
            return new PomCandidatesResponse(List.of(), false, "登记目录不存在、不可读或不是目录。");
        }

        List<String> candidates = new ArrayList<>();
        AtomicInteger visitedEntries = new AtomicInteger();
        boolean[] truncated = {false};
        try {
            Files.walkFileTree(root, EnumSet.noneOf(java.nio.file.FileVisitOption.class), MAX_DISCOVERY_DEPTH,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                            if (!directory.equals(root) && IGNORED_DIRECTORIES.contains(directory.getFileName().toString())) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (visitedEntries.incrementAndGet() > MAX_DISCOVERY_ENTRIES) {
                                truncated[0] = true;
                                return FileVisitResult.TERMINATE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                            if (visitedEntries.incrementAndGet() > MAX_DISCOVERY_ENTRIES) {
                                truncated[0] = true;
                                return FileVisitResult.TERMINATE;
                            }
                            if (file.getFileName().toString().equalsIgnoreCase("pom.xml")
                                    && attributes.isRegularFile() && Files.isReadable(file)) {
                                candidates.add(root.relativize(file).toString());
                                if (candidates.size() >= MAX_POM_CANDIDATES) {
                                    truncated[0] = true;
                                    return FileVisitResult.TERMINATE;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
            return new PomCandidatesResponse(List.copyOf(candidates), truncated[0], null);
        } catch (IOException | RuntimeException exception) {
            return new PomCandidatesResponse(List.copyOf(candidates), truncated[0],
                    "查找过程中遇到无法读取的目录：" + safeMessage(exception));
        }
    }

    public ProjectInspectionResponse inspect(ProjectRecord project, String requestedPomPath) {
        Path root = Path.of(project.rootPath()).toAbsolutePath().normalize();
        List<String> evidence = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        boolean wrapper = hasRegularFile(root.resolve(".mvn/wrapper/maven-wrapper.properties"))
                && (hasRegularFile(root.resolve("mvnw.cmd")) || hasRegularFile(root.resolve("mvnw")));

        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            notices.add("登记路径当前不存在；没有读取任何文件。");
            return response(project, root, requestedPomPath, "PATH_NOT_FOUND", "UNKNOWN", null, wrapper, evidence, notices);
        }
        if (!Files.isDirectory(root)) {
            notices.add("登记路径不是目录；没有读取任何文件。");
            return response(project, root, requestedPomPath, "NOT_DIRECTORY", "UNKNOWN", null, wrapper, evidence, notices);
        }
        if (!Files.isReadable(root)) {
            notices.add("当前进程没有读取该目录的权限。");
            return response(project, root, requestedPomPath, "UNREADABLE", "UNKNOWN", null, wrapper, evidence, notices);
        }

        Path pom;
        try {
            pom = resolvePom(root, requestedPomPath);
        } catch (IllegalArgumentException exception) {
            evidence.add(exception.getMessage());
            return response(project, root, requestedPomPath, "INSPECTED", "UNKNOWN", null, false, evidence, notices);
        }
        if (!hasRegularFile(pom)) {
            evidence.add("所选位置没有 pom.xml，或该文件不可读取。");
            notices.add("请在登记目录中查找并选择 pom.xml 后重新识别。");
            return response(project, root, requestedPomPath, "INSPECTED", "UNKNOWN", null, false, evidence, notices);
        }

        Path moduleRoot = pom.getParent();
        wrapper = hasRegularFile(moduleRoot.resolve(".mvn/wrapper/maven-wrapper.properties"))
                && (hasRegularFile(moduleRoot.resolve("mvnw.cmd")) || hasRegularFile(moduleRoot.resolve("mvnw")));
        String relativePomPath = root.relativize(pom).toString();
        evidence.add("已读取所选 Maven 配置：" + relativePomPath + "。");
        String projectType = "MAVEN_JAVA";
        String javaRequirement = null;
        try {
            PomFacts facts = readPom(pom);
            if (facts.springBoot()) {
                projectType = "SPRING_BOOT_MAVEN";
                evidence.add("pom.xml 声明了 Spring Boot parent、starter 或 Maven 插件。");
            } else {
                evidence.add("pom.xml 可读取，未发现 Spring Boot parent、starter 或 Maven 插件。");
            }
            javaRequirement = facts.javaVersion();
            if (javaRequirement != null) {
                evidence.add("pom.xml 声明的 Java 版本要求为 " + javaRequirement + ".");
            }
        } catch (IOException | SAXException | ParserConfigurationException | RuntimeException exception) {
            notices.add("pom.xml 存在，但无法安全解析：" + safeMessage(exception));
        }

        if (wrapper) {
            evidence.add("发现 Maven Wrapper 配置和启动脚本。");
        } else {
            notices.add("未发现完整的 Maven Wrapper；部署阶段可使用本机 Maven（当前只做识别）。");
        }
        if (hasReadableDirectory(moduleRoot.resolve("src/main/java"))) {
            evidence.add("发现 src/main/java 源码目录。");
        }
        if (hasRegularFile(moduleRoot.resolve("src/main/resources/application.yml"))
                || hasRegularFile(moduleRoot.resolve("src/main/resources/application.yaml"))
                || hasRegularFile(moduleRoot.resolve("src/main/resources/application.properties"))) {
            evidence.add("发现 Spring Boot 常见配置文件。");
            if (projectType.equals("MAVEN_JAVA")) {
                projectType = "SPRING_BOOT_MAVEN";
                notices.add("根据 application 配置文件识别为 Spring Boot 线索；pom.xml 未明确声明 Spring Boot 依赖。");
            }
        }

        notices.add("识别仅读取用户选择的 pom.xml 和该模块的少量固定标记路径；不执行脚本或修改文件。");
        return response(project, root, relativePomPath, "INSPECTED", projectType, javaRequirement, wrapper, evidence, notices);
    }

    private static Path resolvePom(Path root, String requestedPomPath) {
        String selectedPath = requestedPomPath == null || requestedPomPath.isBlank() ? "pom.xml" : requestedPomPath.trim();
        Path relative;
        try {
            relative = Path.of(selectedPath);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("所选 pom.xml 路径格式无效。");
        }
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("只能选择登记目录中的 pom.xml。");
        }
        Path pom = root.resolve(relative).normalize();
        if (!pom.startsWith(root) || pom.getFileName() == null
                || !pom.getFileName().toString().equalsIgnoreCase("pom.xml")) {
            throw new IllegalArgumentException("所选文件必须是登记目录内名为 pom.xml 的文件。");
        }
        try {
            Path realRoot = root.toRealPath();
            Path realPom = pom.toRealPath();
            if (!realPom.startsWith(realRoot) || !Files.isRegularFile(pom, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("所选 pom.xml 必须是登记目录内的普通文件，不能通过符号链接访问。");
            }
        } catch (IOException exception) {
            // Let the caller return a normal inspected result when the selected file does not exist.
        }
        return pom;
    }

    private PomFacts readPom(Path pom) throws IOException, SAXException, ParserConfigurationException {
        try (InputStream input = Files.newInputStream(pom)) {
            byte[] contents = input.readNBytes(MAX_POM_BYTES + 1);
            if (contents.length > MAX_POM_BYTES) {
                throw new IOException("pom.xml 超过 1 MiB 读取上限");
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            Document document = builder.parse(new java.io.ByteArrayInputStream(contents));
            Element project = document.getDocumentElement();
            Element parent = directChild(project, "parent");
            boolean springBoot = parent != null
                    && "org.springframework.boot".equals(text(parent, "groupId"))
                    && "spring-boot-starter-parent".equals(text(parent, "artifactId"));
            springBoot |= hasArtifactStartingWith(document, "spring-boot-starter");
            springBoot |= hasArtifact(document, "spring-boot-maven-plugin");

            Element properties = directChild(project, "properties");
            String javaVersion = properties == null ? null : firstNonBlank(
                    text(properties, "java.version"),
                    text(properties, "maven.compiler.release"),
                    text(properties, "maven.compiler.target"));
            return new PomFacts(springBoot, javaVersion);
        }
    }

    private static boolean hasArtifactStartingWith(Document document, String prefix) {
        NodeList artifacts = document.getElementsByTagNameNS("*", "artifactId");
        for (int index = 0; index < artifacts.getLength(); index++) {
            if (artifacts.item(index).getTextContent().trim().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasArtifact(Document document, String artifactId) {
        NodeList artifacts = document.getElementsByTagNameNS("*", "artifactId");
        for (int index = 0; index < artifacts.getLength(); index++) {
            if (artifactId.equals(artifacts.item(index).getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }

    private static Element directChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            String name = child.getLocalName() == null ? child.getNodeName() : child.getLocalName();
            if (localName.equals(name) && child instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    private static String text(Element parent, String localName) {
        Element child = directChild(parent, localName);
        if (child == null) {
            return null;
        }
        String value = child.getTextContent().trim();
        return value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean hasRegularFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(path);
    }

    private static boolean hasReadableDirectory(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(path);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String singleLine = message.replaceAll("[\\r\\n]+", " ").trim();
        return singleLine.length() > 240 ? singleLine.substring(0, 240) : singleLine;
    }

    private static ProjectInspectionResponse response(
            ProjectRecord project,
            Path root,
            String pomPath,
            String status,
            String projectType,
            String javaVersion,
            boolean wrapper,
            List<String> evidence,
            List<String> notices) {
        return new ProjectInspectionResponse(
                project.id(),
                project.name(),
                root.toString(),
                pomPath,
                Instant.now().toString(),
                status,
                projectType,
                javaVersion,
                wrapper,
                List.copyOf(evidence),
                List.copyOf(notices));
    }

    private record PomFacts(boolean springBoot, String javaVersion) {
    }
}
