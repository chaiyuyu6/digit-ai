package digitai.tools;

import digitai.data.AppPaths;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * MNIST 数据下载器。项目已附带数据；克隆到新机器或数据丢失时可运行：
 * <pre>java -cp out/classes digitai.Main download</pre>
 */
public final class MnistDownloader {

    private static final String BASE = "https://storage.googleapis.com/cvdf-datasets/mnist/";
    private static final String[] FILES = {
            "train-images-idx3-ubyte.gz",
            "train-labels-idx1-ubyte.gz",
            "t10k-images-idx3-ubyte.gz",
            "t10k-labels-idx1-ubyte.gz"
    };

    private MnistDownloader() {
    }

    public static void main(String[] args) throws IOException {
        ensureData(AppPaths.dataDir(), System.out);
    }

    public static void ensureData(Path dataDir, PrintStream out) throws IOException {
        Files.createDirectories(dataDir);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        for (String file : FILES) {
            Path target = dataDir.resolve(file);
            Path raw = dataDir.resolve(file.replace(".gz", ""));
            if (Files.isRegularFile(target) || Files.isRegularFile(raw)) {
                out.println("已存在，跳过: " + file);
                continue;
            }
            out.print("下载 " + file + " ... ");
            out.flush();
            Path part = dataDir.resolve(file + ".part");
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + file))
                    .timeout(Duration.ofMinutes(5)).GET().build();
            try {
                HttpResponse<Path> response = client.send(request,
                        HttpResponse.BodyHandlers.ofFile(part));
                if (response.statusCode() != 200) {
                    Files.deleteIfExists(part);
                    throw new IOException("HTTP " + response.statusCode() + "，下载失败: " + file);
                }
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                out.println("完成 (" + Files.size(target) + " 字节)");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("下载被中断", e);
            }
        }
        out.println("MNIST 数据已就绪: " + dataDir.toAbsolutePath());
    }
}
