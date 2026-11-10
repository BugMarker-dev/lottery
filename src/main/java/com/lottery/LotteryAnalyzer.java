package com.lottery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 历史数据分析 + 加权预测。
 *
 * <p>权重策略：全局频率与近期频率两个分量各自归一化后再混合，因此 weights 之和为 1，
 * 权重即真正的抽样概率：
 * <ul>
 *   <li>全局出现频率（全部期数）占 {@code GLOBAL_WEIGHT}</li>
 *   <li>近期出现频率（最近 recentN 期）占 {@code RECENT_WEIGHT}</li>
 * </ul>
 *
 * <p>两个分量都做了拉普拉斯平滑（分子 +1、分母 +号码上限），保证冷号权重不为 0。
 * 否则可能出现「剩余号码权重全为 0」而选不满所需号码，甚至陷入死循环。
 *
 * <p><b>输入契约</b>：构造器不假设入参顺序，内部会按「最新在前」重新排序 ——
 * 「近 N 期」的统计依赖这个顺序，调用方不需要自己排。
 *
 * <p><b>提醒</b>：彩票每期是独立随机事件，历史频率对下一期没有预测力。
 * 本类只是把「热号」作为一种加权偏好，不提升中奖概率。
 */
public class LotteryAnalyzer {

    private static final int FRONT_MAX = 35;
    private static final int BACK_MAX = 12;
    private static final int FRONT_PICK = 5;
    private static final int BACK_PICK = 2;

    /** 「近期」窗口的默认期数 */
    public static final int DEFAULT_RECENT_N = 30;

    private static final double GLOBAL_WEIGHT = 0.4;
    private static final double RECENT_WEIGHT = 0.6;

    /** 已按「最新在前」排序的历史记录 */
    private final List<HistoryRecord> records;
    private final int recentN;

    private final double[] frontWeights;  // 下标 1..35
    private final double[] backWeights;   // 下标 1..12
    private final int[] frontFreq;
    private final int[] backFreq;
    private final int[] frontRecentFreq;
    private final int[] backRecentFreq;

    private final Random random;

    public LotteryAnalyzer(List<HistoryRecord> records) {
        this(records, DEFAULT_RECENT_N);
    }

    public LotteryAnalyzer(List<HistoryRecord> records, int recentN) {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("历史开奖记录不能为空");
        }
        if (recentN <= 0) {
            throw new IllegalArgumentException("recentN 必须大于 0，当前值：" + recentN);
        }

        this.records = sortLatestFirst(records);
        this.recentN = recentN;

        this.frontFreq = new int[FRONT_MAX + 1];
        this.backFreq = new int[BACK_MAX + 1];
        this.frontRecentFreq = new int[FRONT_MAX + 1];
        this.backRecentFreq = new int[BACK_MAX + 1];
        this.frontWeights = new double[FRONT_MAX + 1];
        this.backWeights = new double[BACK_MAX + 1];
        this.random = new Random();

        analyze();
    }

    // ---------------------------------------------------------------
    // 统计与权重
    // ---------------------------------------------------------------

    private void analyze() {
        int total = records.size();
        int recentCount = Math.min(total, recentN);

        for (HistoryRecord r : records) {
            countNumbers(r.getFront(), frontFreq, FRONT_MAX);
            countNumbers(r.getBack(), backFreq, BACK_MAX);
        }
        for (int i = 0; i < recentCount; i++) {
            HistoryRecord r = records.get(i);
            countNumbers(r.getFront(), frontRecentFreq, FRONT_MAX);
            countNumbers(r.getBack(), backRecentFreq, BACK_MAX);
        }

        computeWeights(frontWeights, frontFreq, frontRecentFreq, FRONT_MAX);
        computeWeights(backWeights, backFreq, backRecentFreq, BACK_MAX);
    }

    /** 越界号码直接忽略，避免脏数据把统计数组打崩 */
    private void countNumbers(List<Integer> numbers, int[] target, int max) {
        if (numbers == null) {
            return;
        }
        for (Integer n : numbers) {
            if (n != null && n >= 1 && n <= max) {
                target[n]++;
            }
        }
    }

    /**
     * 归一化 + 平滑：weights[i] = GLOBAL_WEIGHT * g + RECENT_WEIGHT * r，
     * 其中 g、r 各自是一组和为 1 的概率分布。
     */
    private void computeWeights(double[] weights, int[] globalFreq, int[] recentFreq, int max) {
        long globalSum = 0;
        long recentSum = 0;
        for (int i = 1; i <= max; i++) {
            globalSum += globalFreq[i];
            recentSum += recentFreq[i];
        }

        // 拉普拉斯平滑：分母补上号码上限，保证每个号码都有非零权重
        double globalBase = globalSum + (double) max;
        double recentBase = recentSum + (double) max;

        for (int i = 1; i <= max; i++) {
            double g = globalBase > 0 ? (globalFreq[i] + 1.0) / globalBase : 1.0 / max;
            double r = recentBase > 0 ? (recentFreq[i] + 1.0) / recentBase : 1.0 / max;
            weights[i] = GLOBAL_WEIGHT * g + RECENT_WEIGHT * r;
        }
    }

    // ---------------------------------------------------------------
    // 预测
    // ---------------------------------------------------------------

    /** 生成一注预测号码 */
    public LotteryTicket predict() {
        List<Integer> front = weightedSample(frontWeights, FRONT_MAX, FRONT_PICK);
        List<Integer> back = weightedSample(backWeights, BACK_MAX, BACK_PICK);
        Collections.sort(front);
        Collections.sort(back);
        return new LotteryTicket(front, back);
    }

    /** 批量预测 */
    public List<LotteryTicket> predict(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("预测注数必须大于 0，当前值：" + count);
        }
        List<LotteryTicket> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(predict());
        }
        return list;
    }

    /**
     * 按权重不放回抽样，返回 count 个互不相同的号码（未排序）。
     *
     * <p>抽中的号码权重立即置 0，循环次数固定为 count，因此不存在死循环；
     * 极端情况下（浮点误差导致某一轮没抽到）由末尾的补齐逻辑兜底。
     */
    private List<Integer> weightedSample(double[] weights, int max, int count) {
        double[] remaining = Arrays.copyOf(weights, max + 1);
        List<Integer> result = new ArrayList<>(count);

        for (int k = 0; k < count; k++) {
            double total = 0;
            for (int i = 1; i <= max; i++) {
                total += remaining[i];
            }
            if (total <= 0) {
                break;
            }

            double r = random.nextDouble() * total;
            double cum = 0;
            for (int i = 1; i <= max; i++) {
                cum += remaining[i];
                if (r <= cum) {
                    result.add(i);
                    remaining[i] = 0;
                    break;
                }
            }
        }

        // 兜底补齐，保证号码个数始终满足要求
        for (int i = 1; i <= max && result.size() < count; i++) {
            if (!result.contains(i)) {
                result.add(i);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------
    // 输出
    // ---------------------------------------------------------------

    /** 打印热号 / 冷号统计 */
    public void printStats() {
        int total = records.size();
        int recentCount = Math.min(total, recentN);

        System.out.println("\n====== 历史号码统计（共 " + total + " 期）======");
        System.out.println("【前区】全局热号 TOP 10：");
        printTopN(frontFreq, FRONT_MAX, 10, total);
        System.out.println("【前区】近" + recentCount + "期热号 TOP 10：");
        printTopN(frontRecentFreq, FRONT_MAX, 10, recentCount);
        System.out.println("【后区】全局热号：");
        printTopN(backFreq, BACK_MAX, BACK_MAX, total);
        System.out.println("【后区】近" + recentCount + "期热号：");
        printTopN(backRecentFreq, BACK_MAX, BACK_MAX, recentCount);
        System.out.println();
    }

    /** 打印最近 n 期开奖记录 */
    public void printRecent(int n) {
        int count = Math.min(n, records.size());
        System.out.println("\n====== 最近 " + count + " 期开奖记录 ======");
        for (int i = 0; i < count; i++) {
            System.out.println("  " + records.get(i));
        }
        System.out.println();
    }

    private void printTopN(int[] freq, int max, int topN, int totalDraws) {
        Integer[] indices = new Integer[max];
        for (int i = 0; i < max; i++) {
            indices[i] = i + 1;
        }
        Arrays.sort(indices, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Integer.compare(freq[b], freq[a]);
            }
        });

        StringBuilder sb = new StringBuilder("  ");
        int n = Math.min(topN, max);
        for (int i = 0; i < n; i++) {
            int num = indices[i];
            double pct = totalDraws > 0 ? freq[num] * 100.0 / totalDraws : 0;
            sb.append(String.format("%02d(%d次/%.0f%%)  ", num, freq[num], pct));
        }
        System.out.println(sb.toString());
    }

    // ---------------------------------------------------------------
    // 排序与访问器
    // ---------------------------------------------------------------

    /**
     * 复制一份并按期号（降序）重排，最新的排在最前面。
     * 期号非纯数字时退化为按日期字符串降序，两者都失败则视为 0。
     */
    private static List<HistoryRecord> sortLatestFirst(List<HistoryRecord> input) {
        List<HistoryRecord> list = new ArrayList<>(input);
        Collections.sort(list, new Comparator<HistoryRecord>() {
            @Override
            public int compare(HistoryRecord a, HistoryRecord b) {
                int byIssue = Integer.compare(issueValue(b.getIssue()), issueValue(a.getIssue()));
                if (byIssue != 0) {
                    return byIssue;
                }
                return String.valueOf(b.getDate()).compareTo(String.valueOf(a.getDate()));
            }
        });
        return list;
    }

    private static int issueValue(String issue) {
        if (issue == null) {
            return 0;
        }
        try {
            return Integer.parseInt(issue.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int getRecordCount() {
        return records.size();
    }

    public int getRecentN() {
        return recentN;
    }

    /**
     * 返回最近 n 期开奖记录（最新在前）。供服务层读取，返回的是不可变视图。
     */
    public List<HistoryRecord> getLatest(int n) {
        int count = Math.min(n, records.size());
        return Collections.unmodifiableList(records.subList(0, count));
    }

    /** 下标 1..max 的全局出现次数（副本，防外部篡改统计结果） */
    public int[] getFrontGlobalFreq() {
        return frontFreq.clone();
    }

    public int[] getBackGlobalFreq() {
        return backFreq.clone();
    }

    /** 下标 1..max 的近期（近 recentN 期）出现次数（副本） */
    public int[] getFrontRecentFreq() {
        return frontRecentFreq.clone();
    }

    public int[] getBackRecentFreq() {
        return backRecentFreq.clone();
    }
}
