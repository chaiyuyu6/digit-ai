package digitai.ui;

import digitai.data.AppPaths;
import digitai.data.Dataset;
import digitai.data.MnistLoader;
import digitai.nn.Network;

import javax.swing.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * UI 共享状态：当前网络、MNIST 数据集、模型状态、最后一次推理指标。
 *
 * <p>这是 UI 层内部的中转站，不修改核心 API。数据加载 / 模型加载均在后台线程完成，
 * 回调统一切回 EDT。所有监听器都在 EDT 上被调用。</p>
 */
public final class AppState {

    /** 模型状态，用于状态栏与降级展示。 */
    public enum ModelState {
        /** 从磁盘加载成功。 */
        LOADED,
        /** 训练完成后切换的模型。 */
        TRAINED,
        /** 没有模型文件。 */
        MISSING,
        /** 文件存在但读取失败。 */
        ERROR
    }

    // ---- 网络与模型 ----
    private volatile Network network;
    private volatile ModelState modelState = ModelState.MISSING;
    private volatile String modelMessage = "未加载模型";
    private volatile boolean training;

    // ---- 数据集（懒加载，后台线程） ----
    private volatile Dataset testData;
    private volatile Dataset trainData;
    private boolean testLoading;
    private boolean trainLoading;
    private final List<Consumer<Dataset>> testWaiters = new ArrayList<>();
    private final List<Consumer<String>> testFailWaiters = new ArrayList<>();
    private final List<Consumer<Dataset>> trainWaiters = new ArrayList<>();
    private final List<Consumer<String>> trainFailWaiters = new ArrayList<>();

    // ---- 状态与指标 ----
    private volatile String statusMessage = "就绪";
    private volatile int lastPredicted = -1;
    private volatile double lastInferenceMs = -1;

    // ---- 监听器 ----
    private final CopyOnWriteArrayList<Runnable> networkListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> statusListeners = new CopyOnWriteArrayList<>();

    public AppState() {
    }

    // ==================================================================
    // 网络 / 模型
    // ==================================================================

    public Network network() {
        return network;
    }

    public boolean hasNetwork() {
        return network != null;
    }

    public ModelState modelState() {
        return modelState;
    }

    public String modelMessage() {
        return modelMessage;
    }

    public long parameterCount() {
        Network n = network;
        return n == null ? 0 : n.parameterCount();
    }

    public void setNetwork(Network network, ModelState state, String message) {
        this.network = network;
        this.modelState = state;
        this.modelMessage = message;
        fire(networkListeners);
        fire(statusListeners);
    }

    public void setModelFailed(String message) {
        this.network = null;
        this.modelState = ModelState.ERROR;
        this.modelMessage = message;
        fire(networkListeners);
        fire(statusListeners);
    }

    public void setTraining(boolean training) {
        this.training = training;
        fire(statusListeners);
    }

    public boolean isTraining() {
        return training;
    }

    // ==================================================================
    // 数据集懒加载
    // ==================================================================

    public boolean hasTestData() {
        return testData != null;
    }

    public boolean hasTrainData() {
        return trainData != null;
    }

    public Dataset testData() {
        return testData;
    }

    public Dataset trainData() {
        return trainData;
    }

    /** 确保测试集已加载；已加载时立即在 EDT 回调。 */
    public void withTestData(Consumer<Dataset> onReady, Consumer<String> onError) {
        Dataset d = testData;
        if (d != null) {
            onReady.accept(d);
            return;
        }
        synchronized (this) {
            if (testLoading) {
                testWaiters.add(onReady);
                if (onError != null) {
                    testFailWaiters.add(onError);
                }
                return;
            }
            testLoading = true;
            // 第一次调用者也要登记进等待队列，否则 done() 里没有它的回调
            testWaiters.add(onReady);
            if (onError != null) {
                testFailWaiters.add(onError);
            }
        }
        loadAsync(false);
    }

    /** 确保训练集已加载；已加载时立即在 EDT 回调。 */
    public void withTrainData(Consumer<Dataset> onReady, Consumer<String> onError) {
        Dataset d = trainData;
        if (d != null) {
            onReady.accept(d);
            return;
        }
        synchronized (this) {
            if (trainLoading) {
                trainWaiters.add(onReady);
                if (onError != null) {
                    trainFailWaiters.add(onError);
                }
                return;
            }
            trainLoading = true;
            trainWaiters.add(onReady);
            if (onError != null) {
                trainFailWaiters.add(onError);
            }
        }
        loadAsync(true);
    }

    /** 启动后预热测试集（随机样本 / 训练页缩略图需要），失败静默。 */
    public void prefetchTestData() {
        withTestData(d -> {
        }, err -> {
        });
    }

    /** 仅供离屏截图 / 自测注入已加载的数据集，避免异步等待。 */
    void setTestDataForTest(Dataset data) {
        this.testData = data;
    }

    private void loadAsync(boolean train) {
        SwingWorker<Dataset, Void> worker = new SwingWorker<>() {
            @Override
            protected Dataset doInBackground() throws Exception {
                Path dir = AppPaths.dataDir();
                return train ? MnistLoader.loadTrain(dir) : MnistLoader.loadTest(dir);
            }

            @Override
            protected void done() {
                List<Consumer<Dataset>> oks = new ArrayList<>();
                List<Consumer<String>> fails = new ArrayList<>();
                try {
                    Dataset d = get();
                    if (train) {
                        trainData = d;
                    } else {
                        testData = d;
                    }
                    synchronized (AppState.this) {
                        oks.addAll(train ? trainWaiters : testWaiters);
                        fails.addAll(train ? trainFailWaiters : testFailWaiters);
                        if (train) {
                            trainWaiters.clear();
                            trainFailWaiters.clear();
                            trainLoading = false;
                        } else {
                            testWaiters.clear();
                            testFailWaiters.clear();
                            testLoading = false;
                        }
                    }
                    for (Consumer<Dataset> c : oks) {
                        c.accept(d);
                    }
                } catch (Exception ex) {
                    String msg = friendlyError(ex);
                    synchronized (AppState.this) {
                        fails.addAll(train ? trainFailWaiters : testFailWaiters);
                        if (train) {
                            trainWaiters.clear();
                            trainFailWaiters.clear();
                            trainLoading = false;
                        } else {
                            testWaiters.clear();
                            testFailWaiters.clear();
                            testLoading = false;
                        }
                    }
                    for (Consumer<String> c : fails) {
                        c.accept(msg);
                    }
                }
            }
        };
        worker.execute();
    }

    private static String friendlyError(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getMessage() == null) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    // ==================================================================
    // 状态栏信息
    // ==================================================================

    public void setStatus(String message) {
        this.statusMessage = message == null ? "就绪" : message;
        fire(statusListeners);
    }

    public String status() {
        return statusMessage;
    }

    public void recordInference(int predicted, double millis) {
        this.lastPredicted = predicted;
        this.lastInferenceMs = millis;
        fire(statusListeners);
    }

    public int lastPredicted() {
        return lastPredicted;
    }

    public double lastInferenceMillis() {
        return lastInferenceMs;
    }

    // ==================================================================
    // 监听器
    // ==================================================================

    public void addNetworkListener(Runnable listener) {
        networkListeners.add(listener);
    }

    public void addStatusListener(Runnable listener) {
        statusListeners.add(listener);
    }

    public void notifyStatus() {
        fire(statusListeners);
    }

    private static void fire(List<Runnable> listeners) {
        if (listeners.isEmpty()) {
            return;
        }
        Runnable task = () -> {
            for (Runnable r : listeners) {
                try {
                    r.run();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
