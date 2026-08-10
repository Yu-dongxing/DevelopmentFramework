/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/11
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 项目一键改名工具。
 *
 * <p>使用方式：
 * 1. 直接在本类的 {@link RenameConfig#fromCurrentProject()} 中填写新项目信息。
 * 2. 默认是预览模式，只输出改名计划，不会修改文件。
 * 3. 将 {@code execute} 改为 {@code true} 后，再运行 main 方法即可正式执行。
 */
public final class ProjectRenameTool {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+(\\w+)\\b");
    /**
     * 精确匹配启动类注解，避免将普通类中的 import 语句误识别为启动类。
     */
    private static final Pattern SPRING_BOOT_APPLICATION_ANNOTATION_PATTERN = Pattern.compile(
            "^\\s*@SpringBootApplication(?:\\s*\\([^\\r\\n]*\\))?\\s*$",
            Pattern.MULTILINE
    );
    private static final List<String> TEXT_FILE_SUFFIXES = List.of(
            ".java", ".xml", ".yml", ".yaml", ".properties", ".md", ".txt", ".gitignore", ".iml"
    );
    private static final List<String> SCAN_ROOTS = List.of("src", ".idea", "pom.xml");
    private static final List<String> SOURCE_ROOTS = List.of("src/main/java", "src/test/java");

    private ProjectRenameTool() {
    }

    public static void main(String[] args) throws Exception {
        RenameConfig config = RenameConfig.fromCurrentProject();
        ProjectContext context = ProjectContext.load(config.projectPath());
        RenamePlan plan = RenamePlan.build(context, config);
        plan.printSummary();

        if (!config.execute()) {
            System.out.println();
            System.out.println("当前为预览模式，未修改任何文件。");
            System.out.println("如需正式执行，请把 RenameConfig 中的 execute 改为 true。");
            return;
        }

        plan.execute();
    }

    private record RenameConfig(
            Path projectPath,
            String newProjectName,
            String newGroupId,
            String newBasePackage,
            String newApplicationClassName,
            boolean renameRootDir,
            boolean execute
    ) {

        private static RenameConfig fromCurrentProject() {
            Path projectPath = Path.of(".").toAbsolutePath().normalize();

            // 新项目名：对应 pom.xml 的 artifactId、name、description、IDEA 项目名、application.name 等。
            String newProjectName = "GSPT";
            // 新 Maven groupId：对应 pom.xml 的 groupId。
            String newGroupId = "com.wzz";
            // 新基础包名：对应 package、import、src/main/java、src/test/java 的包目录。
            String newBasePackage = "com.wzz.gspt";
            // 新启动类名：不填时会根据 newProjectName 自动生成，例如 orderCenter -> OrderCenterApplication。
            String newApplicationClassName = null;

            // 是否重命名项目根目录。Windows 下如果 IDE 占用目录，可能会失败。
            boolean renameRootDir = true;
            // 是否正式执行。false 仅预览，true 才会真正修改文件。
            boolean execute = false;

            if (newProjectName == null && newGroupId == null && newBasePackage == null && newApplicationClassName == null) {
                throw new IllegalArgumentException("请先在 RenameConfig 中至少填写一项新值。");
            }

            if (newProjectName != null && newApplicationClassName == null) {
                newApplicationClassName = toApplicationClassName(newProjectName);
            }

            return new RenameConfig(projectPath, newProjectName, newGroupId, newBasePackage, newApplicationClassName, renameRootDir, execute);
        }
    }

    private record ProjectContext(
            Path projectPath,
            String projectName,
            String groupId,
            String basePackage,
            String applicationClassName,
            Path applicationFile
    ) {

        private static ProjectContext load(Path projectPath) throws IOException {
            Path pomPath = projectPath.resolve("pom.xml");
            if (!Files.exists(pomPath)) {
                throw new IllegalArgumentException("未找到 pom.xml: " + pomPath);
            }

            PomCoordinates pomCoordinates = readPomCoordinates(pomPath);
            String groupId = pomCoordinates.groupId();
            String projectName = pomCoordinates.artifactId();

            ApplicationInfo app = findApplicationInfo(projectPath)
                    .orElseThrow(() -> new IllegalStateException("无法定位 @SpringBootApplication 启动类"));

            return new ProjectContext(projectPath, projectName, groupId, app.basePackage(), app.className(), app.file());
        }
    }

    private record ApplicationInfo(String basePackage, String className, Path file) {
    }

    private record PomCoordinates(String groupId, String artifactId, String name) {
    }

    private static final class RenamePlan {

        private final ProjectContext context;
        private final RenameConfig options;
        private final LinkedHashMap<String, String> replacements;
        private final List<Path> contentTargets;
        private final List<DirectoryMove> directoryMoves;
        private final Path oldApplicationFile;
        private final Path newApplicationFile;
        private final Path newRootPath;

        private RenamePlan(
                ProjectContext context,
                RenameConfig options,
                LinkedHashMap<String, String> replacements,
                List<Path> contentTargets,
                List<DirectoryMove> directoryMoves,
                Path oldApplicationFile,
                Path newApplicationFile,
                Path newRootPath
        ) {
            this.context = context;
            this.options = options;
            this.replacements = replacements;
            this.contentTargets = contentTargets;
            this.directoryMoves = directoryMoves;
            this.oldApplicationFile = oldApplicationFile;
            this.newApplicationFile = newApplicationFile;
            this.newRootPath = newRootPath;
        }

        private static RenamePlan build(ProjectContext context, RenameConfig options) throws IOException {
            String targetProjectName = Objects.requireNonNullElse(options.newProjectName(), context.projectName());
            String targetGroupId = Objects.requireNonNullElse(options.newGroupId(), context.groupId());
            String targetBasePackage = Objects.requireNonNullElse(options.newBasePackage(), context.basePackage());
            String targetApplicationClass = Objects.requireNonNullElse(options.newApplicationClassName(), context.applicationClassName());

            LinkedHashMap<String, String> replacements = new LinkedHashMap<>();
            addReplacement(replacements, context.basePackage(), targetBasePackage);
            addReplacement(replacements, context.groupId(), targetGroupId);
            addReplacement(replacements, context.applicationClassName(), targetApplicationClass);
            addReplacement(replacements, context.projectName(), targetProjectName);

            List<Path> contentTargets = collectContentTargets(context.projectPath());
            List<DirectoryMove> directoryMoves = buildDirectoryMoves(context.projectPath(), context.basePackage(), targetBasePackage);
            Path newApplicationFile = computeNewApplicationFile(context.projectPath(), targetBasePackage, targetApplicationClass, context.applicationFile());
            Path newRootPath = options.renameRootDir() && options.newProjectName() != null
                    ? context.projectPath().resolveSibling(targetProjectName)
                    : null;

            return new RenamePlan(context, options, replacements, contentTargets, directoryMoves, context.applicationFile(), newApplicationFile, newRootPath);
        }

        private void printSummary() {
            System.out.println("========== 项目改名预览 ==========");
            System.out.println("项目路径: " + context.projectPath());
            System.out.println("项目名: " + context.projectName() + " -> " + replacementOf(context.projectName()));
            System.out.println("groupId: " + context.groupId() + " -> " + replacementOf(context.groupId()));
            System.out.println("基础包: " + context.basePackage() + " -> " + replacementOf(context.basePackage()));
            System.out.println("启动类: " + context.applicationClassName() + " -> " + replacementOf(context.applicationClassName()));
            System.out.println("文本替换文件数: " + contentTargets.size());

            if (!directoryMoves.isEmpty()) {
                System.out.println("目录迁移:");
                for (DirectoryMove directoryMove : directoryMoves) {
                    System.out.println("  " + directoryMove.source() + " -> " + directoryMove.target());
                }
            }

            if (!oldApplicationFile.equals(newApplicationFile)) {
                System.out.println("启动类文件:");
                System.out.println("  " + oldApplicationFile + " -> " + newApplicationFile);
            }

            if (newRootPath != null) {
                System.out.println("项目根目录:");
                System.out.println("  " + context.projectPath() + " -> " + newRootPath);
            }

            System.out.println("替换规则:");
            replacements.forEach((from, to) -> System.out.println("  " + from + " -> " + to));
        }

        private void execute() throws IOException {
            replaceContent();
            renameApplicationFile();
            moveDirectories();
            writeIdeaProjectName();
            renameRootDirectory();
            System.out.println();
            System.out.println("修改完成。建议随后重新导入 Maven 项目并执行一次 mvn test。");
        }

        private void replaceContent() throws IOException {
            for (Path file : contentTargets) {
                String original = Files.readString(file, StandardCharsets.UTF_8);
                String updated = original;
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    updated = updated.replace(entry.getKey(), entry.getValue());
                }
                if (!updated.equals(original)) {
                    Files.writeString(file, updated, StandardCharsets.UTF_8);
                }
            }
        }

        private void renameApplicationFile() throws IOException {
            if (oldApplicationFile.equals(newApplicationFile) || !Files.exists(oldApplicationFile)) {
                return;
            }
            Files.createDirectories(newApplicationFile.getParent());
            Files.move(oldApplicationFile, newApplicationFile, StandardCopyOption.REPLACE_EXISTING);
        }

        private void moveDirectories() throws IOException {
            for (DirectoryMove directoryMove : directoryMoves) {
                if (!Files.exists(directoryMove.source()) || directoryMove.source().equals(directoryMove.target())) {
                    continue;
                }
                mergeMoveDirectory(directoryMove.source(), directoryMove.target());
                deleteEmptyParents(directoryMove.source(), directoryMove.root());
            }
        }

        private void writeIdeaProjectName() throws IOException {
            if (options.newProjectName() == null) {
                return;
            }
            Path ideaDir = context.projectPath().resolve(".idea");
            if (!Files.isDirectory(ideaDir)) {
                return;
            }
            Files.writeString(ideaDir.resolve(".name"), options.newProjectName(), StandardCharsets.UTF_8);
        }

        private void renameRootDirectory() throws IOException {
            if (newRootPath == null || context.projectPath().equals(newRootPath)) {
                return;
            }
            if (Files.exists(newRootPath)) {
                throw new IllegalStateException("目标根目录已存在，无法重命名: " + newRootPath);
            }
            Files.move(context.projectPath(), newRootPath);
            System.out.println("项目根目录已重命名为: " + newRootPath);
        }

        private String replacementOf(String key) {
            return replacements.getOrDefault(key, key);
        }
    }

    private record DirectoryMove(Path root, Path source, Path target) {
    }

    private static void addReplacement(LinkedHashMap<String, String> replacements, String source, String target) {
        if (source == null || target == null || source.equals(target)) {
            return;
        }
        replacements.put(source, target);
    }

    private static Optional<ApplicationInfo> findApplicationInfo(Path projectPath) throws IOException {
        Path mainJava = projectPath.resolve("src/main/java");
        if (!Files.isDirectory(mainJava)) {
            return Optional.empty();
        }

        try (Stream<Path> stream = Files.walk(mainJava)) {
            List<Path> candidates = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : candidates) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Matcher springBootApplicationMatcher = SPRING_BOOT_APPLICATION_ANNOTATION_PATTERN.matcher(content);
                if (!springBootApplicationMatcher.find()) {
                    continue;
                }
                Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
                if (!packageMatcher.find()) {
                    continue;
                }
                // 从启动类注解之后定位类声明，避免命中注释或 import 中的无关文本。
                Matcher classMatcher = CLASS_PATTERN.matcher(content.substring(springBootApplicationMatcher.start()));
                if (!classMatcher.find()) {
                    continue;
                }
                return Optional.of(new ApplicationInfo(packageMatcher.group(1), classMatcher.group(1), file));
            }
        }
        return Optional.empty();
    }

    private static PomCoordinates readPomCoordinates(Path pomPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(pomPath.toFile());
            Element project = document.getDocumentElement();

            String groupId = directChildText(project, "groupId");
            if (groupId == null) {
                NodeList parentList = project.getElementsByTagName("parent");
                if (parentList.getLength() > 0) {
                    groupId = directChildText((Element) parentList.item(0), "groupId");
                }
            }

            String artifactId = directChildText(project, "artifactId");
            String name = directChildText(project, "name");

            if (groupId == null || artifactId == null) {
                throw new IllegalStateException("无法从 pom.xml 解析 groupId 或 artifactId");
            }
            return new PomCoordinates(groupId, artifactId, name);
        } catch (Exception e) {
            throw new IllegalStateException("解析 pom.xml 失败: " + pomPath, e);
        }
    }

    private static String directChildText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element child)) {
                continue;
            }
            if (tagName.equals(child.getTagName())) {
                return trimToNull(child.getTextContent());
            }
        }
        return null;
    }

    private static List<Path> collectContentTargets(Path projectPath) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String root : SCAN_ROOTS) {
            Path path = projectPath.resolve(root);
            if (!Files.exists(path)) {
                continue;
            }
            if (Files.isRegularFile(path) && isTextFile(path)) {
                files.add(path);
                continue;
            }
            try (Stream<Path> stream = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
                files.addAll(stream
                        .filter(Files::isRegularFile)
                        .filter(ProjectRenameTool::isTextFile)
                        .collect(Collectors.toList()));
            }
        }
        files.sort(Comparator.naturalOrder());
        return files;
    }

    private static boolean isTextFile(Path file) {
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return TEXT_FILE_SUFFIXES.stream().anyMatch(lowerName::endsWith);
    }

    private static List<DirectoryMove> buildDirectoryMoves(Path projectPath, String oldBasePackage, String newBasePackage) {
        if (oldBasePackage.equals(newBasePackage)) {
            return List.of();
        }
        List<DirectoryMove> moves = new ArrayList<>();
        for (String sourceRoot : SOURCE_ROOTS) {
            Path root = projectPath.resolve(sourceRoot);
            Path source = root.resolve(packageToPath(oldBasePackage));
            Path target = root.resolve(packageToPath(newBasePackage));
            moves.add(new DirectoryMove(root, source, target));
        }
        return moves;
    }

    private static Path computeNewApplicationFile(Path projectPath, String newBasePackage, String newApplicationClass, Path currentApplicationFile) {
        Path currentMainRoot = projectPath.resolve("src/main/java");
        if (!currentApplicationFile.startsWith(currentMainRoot)) {
            return currentApplicationFile;
        }
        return currentMainRoot.resolve(packageToPath(newBasePackage)).resolve(newApplicationClass + ".java");
    }

    private static Path packageToPath(String packageName) {
        return Path.of(packageName.replace('.', '/'));
    }

    private static void mergeMoveDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.walk(source)) {
            List<Path> paths = stream.sorted().collect(Collectors.toList());
            for (Path current : paths) {
                Path relative = source.relativize(current);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(current)) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                Files.move(current, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isDirectory(path) && isDirectoryEmpty(path)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static boolean isDirectoryEmpty(Path path) throws IOException {
        try (Stream<Path> stream = Files.list(path)) {
            return stream.findAny().isEmpty();
        }
    }

    private static void deleteEmptyParents(Path from, Path stopAt) throws IOException {
        Path current = from;
        while (current != null && !current.equals(stopAt)) {
            if (!Files.exists(current) || !Files.isDirectory(current) || !isDirectoryEmpty(current)) {
                break;
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String toApplicationClassName(String projectName) {
        String[] parts = projectName.split("[^A-Za-z0-9]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        if (builder.isEmpty()) {
            builder.append("Project");
        }
        if (!builder.toString().endsWith("Application")) {
            builder.append("Application");
        }
        return builder.toString();
    }
}
