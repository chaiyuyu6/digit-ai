package digitai.data;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 路径解析：让程序无论从项目根目录、digit-ai 子目录还是打包后的 jar 启动都能找到数据与模型。
 *
 * <p>优先使用显式配置：系统属性 {@code -Ddigitai.root/-Ddigitai.data/-Ddigitai.model}
 * 或环境变量 {@code DIGIT_AI_ROOT/DIGIT_AI_DATA/DIGIT_AI_MODEL}。</p>
 */
public final class AppPaths {

    private static Path cachedRoot;

    private AppPaths() {
    }

    public static synchronized Path projectRoot() {
        if (cachedRoot != null) {
            return cachedRoot;
        }
        Path explicit = configured("digitai.root", "DIGIT_AI_ROOT");
        if (explicit != null) {
            cachedRoot = explicit.toAbsolutePath().normalize();
            return cachedRoot;
        }

        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path candidate : candidates(cwd)) {
            if (isProjectRoot(candidate)) {
                cachedRoot = candidate;
                return cachedRoot;
            }
        }
        cachedRoot = cwd;
        return cachedRoot;
    }

    /** MNIST 数据目录（内含 4 个 IDX 文件或 .gz）。 */
    public static Path dataDir() {
        Path explicit = configured("digitai.data", "DIGIT_AI_DATA");
        if (explicit != null) {
            return explicit.toAbsolutePath().normalize();
        }
        return projectRoot().resolve("data").resolve("mnist");
    }

    /** 预训练模型文件路径。 */
    public static Path modelFile() {
        Path explicit = configured("digitai.model", "DIGIT_AI_MODEL");
        if (explicit != null) {
            return explicit.toAbsolutePath().normalize();
        }
        return projectRoot().resolve("models").resolve("digit-mlp.bin");
    }

    public static boolean hasData() {
        return MnistLoader.isDataAvailable(dataDir());
    }

    public static boolean hasModel() {
        return Files.isRegularFile(modelFile());
    }

    private static Path configured(String property, String env) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(env);
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value.trim());
    }

    private static Set<Path> candidates(Path cwd) {
        Set<Path> result = new LinkedHashSet<>();
        result.add(cwd);
        result.add(cwd.resolve("digit-ai"));
        for (Path p = cwd.getParent(); p != null; p = p.getParent()) {
            result.add(p);
            result.add(p.resolve("digit-ai"));
        }
        Path jarDir = jarDirectory();
        if (jarDir != null) {
            // 从 jar/class 所在目录一路向上找项目根（out/classes -> out -> 项目根）
            for (Path p = jarDir; p != null; p = p.getParent()) {
                result.add(p);
                result.add(p.resolve("digit-ai"));
            }
        }
        return result;
    }

    private static boolean isProjectRoot(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return false;
        }
        return Files.isDirectory(path.resolve("src/main/java/digitai"))
                || Files.isDirectory(path.resolve("data/mnist"))
                || Files.isRegularFile(path.resolve("models/digit-mlp.bin"));
    }

    private static Path jarDirectory() {
        try {
            Path location = Path.of(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                return location.getParent();
            }
            return Files.isDirectory(location) ? location : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
