package digitai.ui;

import digitai.data.AppPaths;
import digitai.data.Dataset;
import digitai.nn.EpochResult;
import digitai.nn.Evaluation;
import digitai.nn.Network;
import digitai.nn.OptimizerType;
import digitai.nn.TrainConfig;
import digitai.nn.Trainer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 训练页：数据集信息、超参数、开始/暂停/停止、loss & accuracy 曲线、测试样本预测、模型保存/恢复。
 *
 * <p>MNIST 加载、训练、评估、保存全部在 SwingWorker 中执行；每 batch 通过 publish/process
 * 回到 EDT 刷新进度。训练结束后把同一个 {@link Network} 实例交给 AppState，
 * 识别页立即使用新模型。</p>
 */
public final class TrainPanel extends JPanel {

    private final AppState state;
    private final Runnable showRecognition;

    // ---- 超参数控件 ----
    private final Widgets.Stepper epochStepper = new Widgets.Stepper(1, 20, 1, 8);
    private final Widgets.LogSlider lrSlider = new Widgets.LogSlider(0.0001, 0.01, 0.001);
    private final Widgets.Select<Integer> batchSelect;
    private final Widgets.Select<String> structureSelect;
    private final Widgets.Select<OptimizerType> optimizerSelect;

    // ---- 按钮 ----
    private final Widgets.PillButton startBtn;
    private final Widgets.PillButton pauseBtn;
    private final Widgets.PillButton stopBtn;
    private final Widgets.PillButton saveBtn;
    private final Widgets.PillButton restoreBtn;

    // ---- 进度与指标 ----
    private final JLabel phaseLabel = new JLabel("空闲 · 等待开始");
    private final JLabel epochLabel = new JLabel("Epoch — / 8");
    private final Widgets.SlimProgress progressBar = new Widgets.SlimProgress();
    private final Widgets.MetricTile tileEpoch = new Widgets.MetricTile("Epoch", Theme.ACCENT);
    private final Widgets.MetricTile tileBatch = new Widgets.MetricTile("Batch", Theme.ACCENT);
    private final Widgets.MetricTile tileLoss = new Widgets.MetricTile("训练 Loss", Theme.WARNING);
    private final Widgets.MetricTile tileTrainAcc = new Widgets.MetricTile("训练准确率", Theme.VIOLET);
    private final Widgets.MetricTile tileTestAcc = new Widgets.MetricTile("测试准确率", Theme.SUCCESS);
    private final Widgets.MetricTile tileTime = new Widgets.MetricTile("耗时", Theme.TEXT_DIM);

    // ---- 曲线与样本 ----
    private final CurveChart lossChart = new CurveChart("损失 Loss（每个 batch 采样）", false,
            "开始训练后显示 loss 曲线");
    private final CurveChart accChart = new CurveChart("准确率 Accuracy（每个 epoch）", true,
            "每个 epoch 结束后更新训练 / 测试准确率");
    private final SampleStrip samples = new SampleStrip();

    // ---- 数据集信息 ----
    private JLabel trainCountValue;
    private JLabel testCountValue;
    private JLabel dataPathValue;
    private JLabel datasetStateLabel;

    // ---- 训练状态 ----
    private SwingWorker<Void, Progress> worker;
    private volatile boolean paused;
    private volatile boolean stopRequested;
    private long trainStartNanos;
    private int plannedEpochs = 8;
    private Dataset testDataset;

    public TrainPanel(AppState state, Runnable showRecognition) {
        this.state = state;
        this.showRecognition = showRecognition;
        setOpaque(false);
        setBorder(new EmptyBorder(Theme.PAGE_PAD, Theme.PAGE_PAD, Theme.PAGE_PAD, Theme.PAGE_PAD));
        setLayout(new BorderLayout(Theme.COL_GAP, 0));

        batchSelect = new Widgets.Select<>(new Integer[]{16, 32, 64, 128, 256},
                String::valueOf, 64);
        batchSelect.setPreferredSize(new Dimension(88, 30));
        structureSelect = new Widgets.Select<>(
                new String[]{"64 · 32（推荐）", "128 · 64", "32"},
                s -> s, "64 · 32（推荐）");
        structureSelect.setPreferredSize(new Dimension(150, 30));
        optimizerSelect = new Widgets.Select<>(new OptimizerType[]{OptimizerType.ADAM, OptimizerType.SGD},
                o -> o == OptimizerType.ADAM ? "Adam" : "SGD", OptimizerType.ADAM);
        optimizerSelect.setPreferredSize(new Dimension(88, 30));

        startBtn = new Widgets.PillButton("开始训练", Widgets.ButtonKind.PRIMARY);
        pauseBtn = new Widgets.PillButton("暂停", Widgets.ButtonKind.GHOST);
        stopBtn = new Widgets.PillButton("停止", Widgets.ButtonKind.DANGER);
        saveBtn = new Widgets.PillButton("保存模型", Widgets.ButtonKind.GHOST);
        restoreBtn = new Widgets.PillButton("恢复预训练模型", Widgets.ButtonKind.GHOST);

        buildLeftColumn();
        buildRightColumn();
        wireActions();
        refreshDatasetInfo();
        updateIdleMetrics();
        setTrainingUi(false);

        state.addNetworkListener(this::onNetworkChanged);
        lossChart.addSeries("训练损失", Theme.ACCENT);
        accChart.addSeries("训练集", Theme.VIOLET);
        accChart.addSeries("测试集", Theme.SUCCESS);
        accChart.setFixedYRange(0, 1);
    }

    // ==================================================================
    // 左栏
    // ==================================================================

    private void buildLeftColumn() {
        ScrollColumn content = new ScrollColumn();

        // ---- 数据集 ----
        Widgets.Card datasetCard = new Widgets.Card("数据集", "MNIST IDX · 本地文件");
        JPanel rows = Widgets.column(6);
        trainCountValue = valueLabel();
        testCountValue = valueLabel();
        dataPathValue = new JLabel();
        dataPathValue.setFont(Theme.F_MONO_SMALL);
        dataPathValue.setForeground(Theme.TEXT_DIM);
        dataPathValue.setHorizontalAlignment(SwingConstants.RIGHT);
        rows.add(infoRow("训练集", trainCountValue));
        rows.add(infoRow("测试集", testCountValue));
        rows.add(infoRow("数据路径", dataPathValue));
        rows.add(Widgets.vgap(2));
        datasetStateLabel = new JLabel("● 检查中…");
        datasetStateLabel.setFont(Theme.F_SMALL);
        datasetStateLabel.setForeground(Theme.TEXT_FAINT);
        rows.add(datasetStateLabel);
        datasetCard.body().add(rows, BorderLayout.CENTER);

        // ---- 超参数 ----
        Widgets.Card hyperCard = new Widgets.Card("超参数", "修改后点击「开始训练」");
        JPanel form = Widgets.column(9);
        form.add(Widgets.formRow("训练轮数", epochStepper));
        form.add(Widgets.formRow("学习率", lrSlider));
        form.add(Widgets.formRow("Batch Size", batchSelect));
        form.add(Widgets.formRow("隐藏层结构", structureSelect));
        form.add(Widgets.formRow("优化器", optimizerSelect));
        form.add(Widgets.vgap(2));
        JLabel note = Widgets.label("Adam · L2 正则 1e-4 · 每个 epoch 重新打乱", Theme.F_TINY, Theme.TEXT_FAINT);
        form.add(note);
        hyperCard.body().add(form, BorderLayout.CENTER);

        // ---- 训练控制 ----
        Widgets.Card actionCard = new Widgets.Card("训练控制", "60,000 张图在后台训练，界面不卡顿");
        JPanel actions = Widgets.column(9);
        actions.add(fullWidth(startBtn, 40));
        JPanel row1 = new JPanel(new GridLayout(1, 2, 8, 0));
        row1.setOpaque(false);
        row1.add(pauseBtn);
        row1.add(stopBtn);
        actions.add(fullWidth(row1, 34));
        actions.add(Widgets.divider());
        JPanel row2 = new JPanel(new GridLayout(1, 2, 8, 0));
        row2.setOpaque(false);
        row2.add(saveBtn);
        row2.add(restoreBtn);
        actions.add(fullWidth(row2, 34));
        JLabel hint = Widgets.label("训练完成后自动应用到识别页", Theme.F_TINY, Theme.TEXT_FAINT);
        actions.add(hint);
        actionCard.body().add(actions, BorderLayout.CENTER);

        content.add(card(datasetCard));
        content.add(Widgets.vgap(Theme.COL_GAP));
        content.add(card(hyperCard));
        content.add(Widgets.vgap(Theme.COL_GAP));
        content.add(card(actionCard));
        content.add(Widgets.vgap(4));

        JScrollPane scroll = Theme.scroll(content);
        scroll.setPreferredSize(new Dimension(350, 600));
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll, BorderLayout.WEST);
    }

    private JLabel valueLabel() {
        JLabel label = new JLabel("—");
        label.setFont(Theme.F_MONO_SMALL);
        label.setForeground(Theme.TEXT);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    private JComponent infoRow(String name, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(Widgets.label(name, Theme.F_SMALL, Theme.TEXT_DIM), BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.setPreferredSize(new Dimension(200, 22));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private Widgets.Card card(Widgets.Card c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    // ==================================================================
    // 右栏
    // ==================================================================

    private void buildRightColumn() {
        JPanel right = new JPanel(new BorderLayout(0, Theme.COL_GAP));
        right.setOpaque(false);

        // ---- 进度 ----
        Widgets.Card progressCard = new Widgets.Card("训练进度", "实时 loss / accuracy");        phaseLabel.setFont(Theme.ui(Font.BOLD, 12f));
        phaseLabel.setForeground(Theme.ACCENT);
        epochLabel.setFont(Theme.F_MONO_SMALL);
        epochLabel.setForeground(Theme.TEXT_DIM);
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(phaseLabel, BorderLayout.WEST);
        head.add(epochLabel, BorderLayout.EAST);

        JPanel barWrap = new JPanel(new BorderLayout());
        barWrap.setOpaque(false);
        barWrap.setBorder(new EmptyBorder(7, 0, 6, 0));
        barWrap.add(progressBar, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(barWrap, BorderLayout.CENTER);

        JPanel metrics = new JPanel(new GridLayout(2, 3, 8, 6));
        metrics.setOpaque(false);
        Widgets.MetricTile[] tiles = {tileEpoch, tileBatch, tileLoss,
                tileTrainAcc, tileTestAcc, tileTime};
        for (Widgets.MetricTile tile : tiles) {
            tile.setPreferredSize(new Dimension(100, 54));
            metrics.add(tile);
        }
        progressCard.body().add(top, BorderLayout.NORTH);
        progressCard.body().add(metrics, BorderLayout.CENTER);

        // ---- 曲线 ----
        Widgets.Card chartCard = new Widgets.Card("训练曲线", "loss 按 batch 采样 · 准确率按 epoch");
        JPanel charts = new JPanel(new GridLayout(2, 1, 0, 8));
        charts.setOpaque(false);
        charts.add(lossChart);
        charts.add(accChart);
        chartCard.body().add(charts, BorderLayout.CENTER);

        // ---- 测试样本 ----
        Widgets.Card sampleCard = new Widgets.Card("实时测试样本", "预测 vs 真值 · 训练 / 评估后刷新");
        samples.setPreferredSize(new Dimension(600, 72));
        sampleCard.body().add(samples, BorderLayout.CENTER);

        progressCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        // 曲线区给固定首选高度：窗口变矮时整列滚动，而不是把图表压扁
        chartCard.setPreferredSize(new Dimension(600, 268));
        right.add(progressCard, BorderLayout.NORTH);
        right.add(chartCard, BorderLayout.CENTER);
        right.add(sampleCard, BorderLayout.SOUTH);

        JScrollPane rightScroll = Theme.scroll(new ScrollableFill(right));
        rightScroll.setBorder(null);
        add(rightScroll, BorderLayout.CENTER);
    }

    // ==================================================================
    // 事件
    // ==================================================================

    private void wireActions() {
        startBtn.addActionListener(e -> startTraining());
        pauseBtn.addActionListener(e -> togglePause());
        stopBtn.addActionListener(e -> stopTraining());
        saveBtn.addActionListener(e -> saveModel());
        restoreBtn.addActionListener(e -> restoreModel());
    }

    private void startTraining() {
        if (state.isTraining() || worker != null) {
            return;
        }
        if (!AppPaths.hasData()) {
            Widgets.error(this, "数据集缺失",
                    "未找到 MNIST 数据文件。\n请确认 data/mnist 下存在 4 个 IDX 文件（或 .gz）。\n\n"
                            + AppPaths.dataDir());
            return;
        }
        plannedEpochs = epochStepper.value();
        paused = false;
        stopRequested = false;
        pauseBtn.setText("暂停");
        stopBtn.setEnabled(true);
        setTrainingUi(true);
        state.setTraining(true);
        phaseLabel.setText("正在加载训练集（60,000 张）…");
        phaseLabel.setForeground(Theme.WARNING);
        epochLabel.setText("Epoch 0 / " + plannedEpochs);
        progressBar.setFraction(0);
        resetCharts();
        updateIdleMetrics();
        state.setStatus("正在加载 MNIST 训练集…");

        state.withTrainData(train -> {
            if (stopRequested) {
                finishStoppedBeforeStart();
                return;
            }
            if (state.hasTestData()) {
                launchWorker(train, state.testData());
                return;
            }
            phaseLabel.setText("正在加载测试集（10,000 张）…");
            state.withTestData(test -> {
                if (stopRequested) {
                    finishStoppedBeforeStart();
                } else {
                    launchWorker(train, test);
                }
            }, this::onDataError);
        }, this::onDataError);
    }

    private void resetCharts() {
        lossChart.clearData();
        accChart.clearData();
        lossChart.setXRange(0, plannedEpochs);
        accChart.setXRange(0, plannedEpochs);
        accChart.setFixedYRange(0, 1);
        lossChart.setAutoY();
    }

    private void launchWorker(Dataset train, Dataset test) {
        testDataset = test;
        int[] sizes = switch (structureSelect.selectedIndex()) {
            case 1 -> new int[]{784, 128, 64, 10};
            case 2 -> new int[]{784, 32, 10};
            default -> new int[]{784, 64, 32, 10};
        };
        TrainConfig config = TrainConfig.defaults()
                .withEpochs(epochStepper.value())
                .withLearningRate(lrSlider.value())
                .withBatchSize(batchSelect.value())
                .withOptimizer(optimizerSelect.value());
        Network network = new Network(config.seed(), sizes);
        Trainer trainer = new Trainer(network, config);
        trainStartNanos = System.nanoTime();
        phaseLabel.setText("训练中…");
        phaseLabel.setForeground(Theme.ACCENT);
        state.setStatus("训练中：Epoch 1 / " + plannedEpochs);

        final Evaluation[] finalEval = new Evaluation[1];
        SwingWorker<Void, Progress> w = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                int totalBatches = (train.size() + config.batchSize() - 1) / config.batchSize();
                int stride = Math.max(1, totalBatches / 160);
                for (int epoch = 1; epoch <= config.epochs(); epoch++) {
                    if (stopRequested || isCancelled()) {
                        break;
                    }
                    final int ep = epoch;
                    EpochResult result = trainer.trainEpoch(train, (batchIndex, total, runningLoss) -> {
                        if (stopRequested) {
                            return false;
                        }
                        while (paused) {
                            if (stopRequested) {
                                return false;
                            }
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return false;
                            }
                        }
                        if (stopRequested) {
                            return false;
                        }
                        if ((batchIndex + 1) % stride == 0 || batchIndex + 1 == total) {
                            publish(new Progress(ep, batchIndex + 1, total, runningLoss,
                                    Double.NaN, Double.NaN, elapsed(), "训练中"));
                        }
                        return true;
                    });
                    if (stopRequested) {
                        break;
                    }
                    Evaluation eval = trainer.evaluate(test, Math.min(2000, test.size()));
                    publish(new Progress(ep, result.batches(), result.batches(), result.loss(),
                            result.trainAccuracy(), eval.accuracy(), elapsed(), "评估中"));
                }
                if (!stopRequested && !isCancelled()) {
                    publish(new Progress(config.epochs(), 0, 0, Double.NaN, Double.NaN,
                            Double.NaN, elapsed(), "正在评估完整测试集…"));
                    finalEval[0] = trainer.evaluate(test);
                }
                return null;
            }

            @Override
            protected void process(List<Progress> chunks) {
                for (Progress p : chunks) {
                    applyProgress(p);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    onWorkerFailed(ex);
                    return;
                }
                onWorkerDone(network, finalEval[0]);
            }
        };
        worker = w;
        w.execute();
    }

    private void applyProgress(Progress p) {
        if (p.epoch <= 0) {
            return;
        }
        phaseLabel.setText(p.phase);
        tileEpoch.setValue(p.epoch + " / " + plannedEpochs);
        epochLabel.setText("Epoch " + p.epoch + " / " + plannedEpochs);
        tileTime.setValue(Theme.fmtDuration(p.elapsedMs));
        if (p.totalBatches > 0) {
            double within = (p.epoch - 1 + p.batch / (double) p.totalBatches) / plannedEpochs;
            progressBar.setFraction(within);
            tileBatch.setValue(p.batch + " / " + p.totalBatches);
            if (!Double.isNaN(p.loss)) {
                tileLoss.setValue(String.format("%.4f", p.loss));
                lossChart.addPoint(0, p.epoch - 1 + p.batch / (double) p.totalBatches, p.loss);
            }
            state.setStatus("训练中：Epoch " + p.epoch + " / " + plannedEpochs
                    + " · batch " + p.batch + " / " + p.totalBatches
                    + " · loss " + (Double.isNaN(p.loss) ? "—" : String.format("%.4f", p.loss)));
        } else {
            progressBar.setFraction((p.epoch - 1) / (double) plannedEpochs);
        }
        if (!Double.isNaN(p.trainAcc)) {
            tileTrainAcc.setValue(Theme.pct(p.trainAcc, 1));
            accChart.addPoint(0, p.epoch, p.trainAcc);
        }
        if (!Double.isNaN(p.testAcc)) {
            tileTestAcc.setValue(Theme.pct(p.testAcc, 1));
            accChart.addPoint(1, p.epoch, p.testAcc);
        }
    }

    private void onWorkerDone(Network network, Evaluation finalEval) {
        boolean stopped = stopRequested;
        worker = null;
        state.setTraining(false);
        setTrainingUi(false);
        if (stopped) {
            phaseLabel.setText("已停止 · 本次训练结果未应用");
            phaseLabel.setForeground(Theme.WARNING);
            epochLabel.setText("已停止");
            state.setStatus("训练已停止，结果未应用");
            pauseBtn.setText("暂停");
            stopBtn.setEnabled(false);
            return;
        }
        phaseLabel.setText("训练完成 · 新模型已应用到识别页");
        phaseLabel.setForeground(Theme.SUCCESS);
        progressBar.setFraction(1.0);
        if (finalEval != null) {
            tileTestAcc.setValue(Theme.pct(finalEval.accuracy(), 2));
            state.setStatus("训练完成 · 测试准确率 " + Theme.pct(finalEval.accuracy(), 2)
                    + "，已应用到识别页");
        } else {
            state.setStatus("训练完成，已应用到识别页");
        }
        state.setNetwork(network, AppState.ModelState.TRAINED, "训练完成 · 新模型已启用");
        refreshSamples(network);
        saveBtn.setEnabled(true);
    }

    private void onWorkerFailed(Exception ex) {
        worker = null;
        state.setTraining(false);
        setTrainingUi(false);
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        String msg = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        phaseLabel.setText("训练失败");
        phaseLabel.setForeground(Theme.DANGER);
        epochLabel.setText("已中止");
        state.setStatus("训练失败：" + msg);
        Widgets.error(this, "训练失败", "训练过程中发生错误：\n" + msg);
    }

    private void finishStoppedBeforeStart() {
        worker = null;
        state.setTraining(false);
        setTrainingUi(false);
        phaseLabel.setText("已取消");
        phaseLabel.setForeground(Theme.TEXT_DIM);
        state.setStatus("已取消本次训练");
        stopRequested = false;
    }

    private void togglePause() {
        if (worker == null) {
            return;
        }
        paused = !paused;
        pauseBtn.setText(paused ? "继续" : "暂停");
        phaseLabel.setText(paused ? "已暂停 · 当前 batch 结束后停住" : "训练中…");
        phaseLabel.setForeground(paused ? Theme.WARNING : Theme.ACCENT);
        state.setStatus(paused ? "训练已暂停" : "训练继续…");
    }

    private void stopTraining() {
        if (worker == null && !state.isTraining()) {
            return;
        }
        stopRequested = true;
        paused = false;
        pauseBtn.setText("暂停");
        stopBtn.setEnabled(false);
        phaseLabel.setText("正在停止…");
        phaseLabel.setForeground(Theme.WARNING);
        state.setStatus("正在停止训练…");
    }

    private void onDataError(String message) {
        worker = null;
        state.setTraining(false);
        setTrainingUi(false);
        phaseLabel.setText("数据加载失败");
        phaseLabel.setForeground(Theme.DANGER);
        state.setStatus("MNIST 数据加载失败：" + message);
        Widgets.error(this, "数据加载失败",
                "无法加载 MNIST 数据集：\n" + message + "\n\n请检查 " + AppPaths.dataDir());
    }

    private void saveModel() {
        Network network = state.network();
        if (network == null) {
            Widgets.error(this, "没有可保存的模型", "请先加载预训练模型或完成一次训练。");
            return;
        }
        Path file = AppPaths.modelFile();
        saveBtn.setEnabled(false);
        state.setStatus("正在保存模型…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                network.save(file);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    state.setStatus("模型已保存：" + file);
                    Widgets.info(TrainPanel.this, "模型已保存",
                            "已保存到：\n" + file + "\n\n参数数量：" + Theme.fmtInt(network.parameterCount()));
                } catch (Exception ex) {
                    state.setStatus("模型保存失败：" + ex.getMessage());
                    Widgets.error(TrainPanel.this, "模型保存失败",
                            "写入文件失败：\n" + file + "\n\n" + ex.getMessage());
                } finally {
                    saveBtn.setEnabled(!state.isTraining());
                }
            }
        }.execute();
    }

    private void restoreModel() {
        if (state.isTraining()) {
            return;
        }
        Path file = AppPaths.modelFile();
        if (!Files.isRegularFile(file)) {
            Widgets.error(this, "没有预训练模型",
                    "未找到模型文件：\n" + file + "\n\n请先训练并保存，或把模型放入 models/ 目录。");
            return;
        }
        phaseLabel.setText("正在加载预训练模型…");
        phaseLabel.setForeground(Theme.WARNING);
        state.setStatus("正在加载预训练模型…");
        restoreBtn.setEnabled(false);
        new SwingWorker<Network, Void>() {
            @Override
            protected Network doInBackground() throws Exception {
                return Network.load(file);
            }

            @Override
            protected void done() {
                try {
                    Network network = get();
                    state.setNetwork(network, AppState.ModelState.LOADED,
                            "已恢复预训练模型 · " + file.getFileName());
                    phaseLabel.setText("已恢复预训练模型");
                    phaseLabel.setForeground(Theme.SUCCESS);
                    state.setStatus("已恢复预训练模型（参数 " + Theme.fmtInt(network.parameterCount()) + "）");
                    refreshSamples(network);
                } catch (Exception ex) {
                    phaseLabel.setText("模型加载失败");
                    phaseLabel.setForeground(Theme.DANGER);
                    state.setStatus("模型加载失败：" + ex.getMessage());
                    Widgets.error(TrainPanel.this, "模型加载失败",
                            "无法读取模型文件：\n" + file + "\n\n" + ex.getMessage());
                } finally {
                    restoreBtn.setEnabled(!state.isTraining());
                }
            }
        }.execute();
    }

    // ==================================================================
    // 状态刷新
    // ==================================================================

    private void refreshDatasetInfo() {
        boolean has = AppPaths.hasData();
        Dataset train = state.trainData();
        Dataset test = state.testData();
        if (trainCountValue != null) {
            trainCountValue.setText(train != null ? Theme.fmtInt(train.size()) + " 张"
                    : has ? "60,000 张" : "缺失");
            trainCountValue.setForeground(has ? Theme.TEXT : Theme.DANGER);
            testCountValue.setText(test != null ? Theme.fmtInt(test.size()) + " 张"
                    : has ? "10,000 张" : "缺失");
            testCountValue.setForeground(has ? Theme.TEXT : Theme.DANGER);
            String path;
            try {
                Path root = AppPaths.projectRoot();
                Path dir = AppPaths.dataDir();
                path = dir.startsWith(root) ? root.relativize(dir).toString() : dir.toString();
            } catch (RuntimeException ex) {
                path = AppPaths.dataDir().toString();
            }
            if (path.length() > 36) {
                path = "…" + path.substring(path.length() - 35);
            }
            dataPathValue.setText(path);
            dataPathValue.setToolTipText(AppPaths.dataDir().toString());
            if (has) {
                datasetStateLabel.setText("● IDX 文件已就绪");
                datasetStateLabel.setForeground(Theme.SUCCESS);
            } else {
                datasetStateLabel.setText("● 未找到 IDX 文件，训练不可用");
                datasetStateLabel.setForeground(Theme.DANGER);
            }
        }
        if (!state.isTraining()) {
            startBtn.setEnabled(has);
        }
    }

    private void updateIdleMetrics() {
        tileBatch.setValue("—");
        tileLoss.setValue("—");
        tileTrainAcc.setValue("—");
        tileTestAcc.setValue("—");
        tileTime.setValue("—");
        tileEpoch.setValue("0 / " + plannedEpochs);
    }

    private void setTrainingUi(boolean training) {
        epochStepper.setEnabled(!training);
        lrSlider.setEnabled(!training);
        batchSelect.setEnabled(!training);
        structureSelect.setEnabled(!training);
        optimizerSelect.setEnabled(!training);
        startBtn.setEnabled(!training && AppPaths.hasData());
        pauseBtn.setEnabled(training);
        stopBtn.setEnabled(training);
        saveBtn.setEnabled(!training && state.hasNetwork());
        restoreBtn.setEnabled(!training);
    }

    private void onNetworkChanged() {
        if (state.isTraining()) {
            return;
        }
        saveBtn.setEnabled(state.hasNetwork());
        Network network = state.network();
        if (network != null) {
            refreshSamples(network);
        } else {
            samples.clear();
        }
    }

    private void refreshSamples(Network network) {
        if (network == null) {
            samples.clear();
            return;
        }
        Dataset test = state.testData();
        if (test == null) {
            samples.clear();
            state.withTestData(d -> {
                Network current = state.network();
                if (current != null) {
                    refreshSamples(current);
                }
            }, err -> {
            });
            return;
        }
        int count = Math.min(10, test.size());
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = (i * 1009 + 37) % test.size();
        }
        samples.setSource(test, indices);
        samples.refresh(network);
    }

    // ==================================================================
    // 供 MainFrame 调用
    // ==================================================================

    /** 切换到训练页时刷新数据集状态与测试样本。 */
    public void onShown() {
        refreshDatasetInfo();
        if (state.hasNetwork()) {
            refreshSamples(state.network());
        } else {
            samples.clear();
        }
    }

    /** 供截图 / 自测：返回最近一次训练的曲线图。 */
    CurveChart lossChartForTest() {
        return lossChart;
    }

    CurveChart accuracyChartForTest() {
        return accChart;
    }

    SampleStrip samplesForTest() {
        return samples;
    }

    JLabel phaseLabelForTest() {
        return phaseLabel;
    }

    /** 仅供自动化冒烟测试：用指定参数启动一次真实训练。 */
    void startTrainingForTest(int epochs, int batchSize) {
        int[] batches = {16, 32, 64, 128, 256};
        int idx = 0;
        for (int i = 0; i < batches.length; i++) {
            if (batches[i] == batchSize) {
                idx = i;
            }
        }
        batchSelect.setSelectedIndexSilently(idx);
        epochStepper.setValue(epochs);
        startTraining();
    }

    /** 仅供离屏截图：填充一份「训练进行中」的演示数据，便于检查曲线与指标排版。 */
    void seedDemoVisualsForTest() {
        plannedEpochs = 8;
        lossChart.clearData();
        accChart.clearData();
        lossChart.setXRange(0, plannedEpochs);
        accChart.setXRange(0, plannedEpochs);
        accChart.setFixedYRange(0, 1);
        lossChart.setAutoY();
        double lastLoss = 0;
        for (int i = 0; i < 240; i++) {
            double x = i * 6.0 / 240.0; // 覆盖前 6 个 epoch
            double loss = 1.05 * Math.exp(-x * 0.72) + 0.07 + 0.028 * Math.sin(i * 0.6);
            lastLoss = loss;
            lossChart.addPoint(0, x, loss);
        }
        double[] trainAcc = {0.872, 0.941, 0.958, 0.967, 0.973, 0.978};
        double[] testAcc = {0.879, 0.948, 0.962, 0.969, 0.973, 0.976};
        for (int i = 0; i < trainAcc.length; i++) {
            accChart.addPoint(0, i + 1, trainAcc[i]);
            accChart.addPoint(1, i + 1, testAcc[i]);
        }
        tileEpoch.setValue("6 / 8");
        tileBatch.setValue("492 / 938");
        tileLoss.setValue(String.format("%.4f", lastLoss));
        tileTrainAcc.setValue("97.8%");
        tileTestAcc.setValue("97.6%");
        tileTime.setValue("1 分 42 秒");
        progressBar.setFraction(0.67);
        phaseLabel.setText("训练中…");
        phaseLabel.setForeground(Theme.ACCENT);
        epochLabel.setText("Epoch 6 / 8");
        state.setTraining(true);
        setTrainingUi(true);
        refreshSamples(state.network());
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private long elapsed() {
        return (System.nanoTime() - trainStartNanos) / 1_000_000L;
    }

    private static <T extends JComponent> T fullWidth(T component, int height) {
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setPreferredSize(new Dimension(200, height));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    /** 每 batch / epoch 回传的进度快照。 */
    private record Progress(int epoch, int batch, int totalBatches, double loss,
                            double trainAcc, double testAcc, long elapsedMs, String phase) {
    }

    /** 可滚动且自动占满视口宽度的普通布局容器（用于训练页右栏）。 */
    private static final class ScrollableFill extends JPanel implements Scrollable {
        ScrollableFill(Component content) {
            super(new BorderLayout());
            setOpaque(false);
            add(content, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 140;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            // 窗口足够高时铺满（图表随窗口拉高）；窗口变矮时退回滚动
            Container parent = getParent();
            return parent instanceof JViewport viewport
                    && viewport.getHeight() > super.getPreferredSize().height;
        }
    }

    /** 可滚动且自动占满视口宽度的纵向容器。 */
    private static final class ScrollColumn extends JPanel implements Scrollable {
        ScrollColumn() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(1, 1, 8, 4));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 120;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
