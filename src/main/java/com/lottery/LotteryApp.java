package com.lottery;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 大乐透号码生成器 - 主入口
 * 用法：
 *   java -jar lottery.jar          交互模式
 *   java -jar lottery.jar 5        直接随机生成 5 注
 *   java -jar lottery.jar p 5      拉取历史数据并预测生成 5 注
 */
public class LotteryApp {

    /** 不指定注数时的默认值，交互模式与 CLI 模式保持一致 */
    private static final int DEFAULT_BATCH_COUNT = 5;

    /** parsePositive 的兜底值，表示「不是合法的正整数」 */
    private static final int INVALID_COUNT = -1;

    /** 交互模式下加载的历史数据期数 */
    private static final int HISTORY_PAGE_SIZE = 100;

    public static void main(String[] args) {
        if (args.length > 0) {
            handleCli(args);
            return;
        }
        runInteractive();
    }

    // ---------------------------------------------------------------
    // CLI 模式
    // ---------------------------------------------------------------

    private static void handleCli(String[] args) {
        LotteryGenerator generator = new LotteryGenerator();

        if ("p".equalsIgnoreCase(args[0])) {
            // 预测模式：java -jar lottery.jar p [注数]
            int count = args.length > 1
                    ? parsePositive(args[1], DEFAULT_BATCH_COUNT)
                    : DEFAULT_BATCH_COUNT;
            LotteryAnalyzer analyzer = loadAnalyzer(HISTORY_PAGE_SIZE);
            if (analyzer == null) {
                return;
            }
            printTickets(analyzer.predict(count), "预测");
        } else {
            // 随机模式：java -jar lottery.jar [注数]
            int count = parsePositive(args[0], INVALID_COUNT);
            if (count < 0) {
                System.out.println("参数错误，请传入正整数，例如：java -jar lottery.jar 5");
                return;
            }
            printRandomTickets(generator, count);
        }
    }

    // ---------------------------------------------------------------
    // 交互模式
    // ---------------------------------------------------------------

    private static void runInteractive() {
        LotteryGenerator generator = new LotteryGenerator();
        LotteryAnalyzer analyzer = null;

        Scanner scanner = new Scanner(System.in);
        printBanner();

        while (true) {
            System.out.print("\n请输入指令（? 查看帮助，0 退出）：");
            if (!scanner.hasNextLine()) {
                System.out.println("\n输入结束，退出。");
                return;
            }
            String input = scanner.nextLine().trim();

            switch (input.toLowerCase()) {
                case "0":
                case "exit":
                case "quit":
                    System.out.println("退出。");
                    return;

                case "?":
                case "help":
                    printHelp();
                    break;

                case "v":
                    handleValidate(scanner, generator);
                    break;

                case "s":
                    analyzer = ensureAnalyzer(analyzer);
                    if (analyzer != null) {
                        analyzer.printStats();
                    }
                    break;

                case "r":
                    analyzer = ensureAnalyzer(analyzer);
                    if (analyzer != null) {
                        analyzer.printRecent(10);
                    }
                    break;

                case "reload":
                    System.out.println("重新拉取历史数据...");
                    analyzer = loadAnalyzer(HISTORY_PAGE_SIZE);
                    break;

                default:
                    analyzer = handleNumberInput(input, generator, analyzer);
                    break;
            }
        }
    }

    /**
     * 处理数字输入：支持 "5"（随机）或 "p5" / "p 5"（预测）。
     * 返回最新的 analyzer，便于调用方复用已加载的历史数据。
     */
    private static LotteryAnalyzer handleNumberInput(String input, LotteryGenerator generator,
                                                     LotteryAnalyzer analyzer) {
        boolean predict = false;
        String numStr = input;

        if (input.toLowerCase().startsWith("p")) {
            predict = true;
            numStr = input.substring(1).trim();
        }

        if (numStr.isEmpty()) {
            numStr = String.valueOf(DEFAULT_BATCH_COUNT);
        }

        int count = parsePositive(numStr, INVALID_COUNT);
        if (count < 0) {
            System.out.println("无效输入。输入 ? 查看帮助。");
            return analyzer;
        }

        if (predict) {
            LotteryAnalyzer ready = ensureAnalyzer(analyzer);
            if (ready != null) {
                printTickets(ready.predict(count), "预测");
            }
            return ready;
        }
        printRandomTickets(generator, count);
        return analyzer;
    }

    // ---------------------------------------------------------------
    // 工具方法
    // ---------------------------------------------------------------

    private static LotteryAnalyzer ensureAnalyzer(LotteryAnalyzer existing) {
        if (existing != null) {
            return existing;
        }
        return loadAnalyzer(HISTORY_PAGE_SIZE);
    }

    private static LotteryAnalyzer loadAnalyzer(int pageSize) {
        System.out.println("正在拉取历史开奖数据（最近 " + pageSize + " 期）...");
        try {
            List<HistoryRecord> records = new HistoryFetcher().fetch(pageSize);
            System.out.println("成功加载 " + records.size() + " 期历史数据。");
            return new LotteryAnalyzer(records);
        } catch (Exception e) {
            System.out.println("拉取历史数据失败：" + e.getMessage());
            System.out.println("请检查网络连接后输入 reload 重试。");
            return null;
        }
    }

    private static void printRandomTickets(LotteryGenerator generator, int count) {
        try {
            printTickets(generator.generate(count), "随机");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void printTickets(List<LotteryTicket> tickets, String mode) {
        System.out.println("------------------------------------");
        System.out.println("  [" + mode + "模式]  共 " + tickets.size() + " 注");
        System.out.println("------------------------------------");
        for (int i = 0; i < tickets.size(); i++) {
            System.out.printf("  第 %02d 注  %s%n", i + 1, tickets.get(i));
        }
        System.out.println("------------------------------------");
    }

    private static void handleValidate(Scanner scanner, LotteryGenerator generator) {
        System.out.println("\n--- 号码校验 ---");
        System.out.print("请输入前区 5 个号码（空格分隔，如：01 05 12 20 30）：");
        String frontInput = scanner.nextLine().trim();
        System.out.print("请输入后区 2 个号码（空格分隔，如：03 08）：");
        String backInput = scanner.nextLine().trim();

        try {
            LotteryTicket ticket = new LotteryTicket(parseNumbers(frontInput), parseNumbers(backInput));
            if (generator.validate(ticket)) {
                System.out.println("校验通过！号码：" + ticket);
            } else {
                System.out.println("号码不合法！前区需 5 个不重复号码(01-35)，后区需 2 个不重复号码(01-12)。");
            }
        } catch (NumberFormatException e) {
            System.out.println("解析失败，请确认输入的全是数字。");
        }
    }

    private static void printBanner() {
        System.out.println("============================================");
        System.out.println("         大乐透号码生成器  v2.1");
        System.out.println("  前区 01-35 选5个  |  后区 01-12 选2个");
        System.out.println("============================================");
        System.out.println("输入 ? 查看帮助");
    }

    private static void printHelp() {
        System.out.println("\n  指令说明：");
        System.out.println("  [数字]     随机生成指定注数，如：5（不填默认 " + DEFAULT_BATCH_COUNT + "）");
        System.out.println("  p[数字]    基于历史数据预测生成，如：p5（不填默认 " + DEFAULT_BATCH_COUNT + "）");
        System.out.println("  s          查看热号/冷号统计");
        System.out.println("  r          查看最近 10 期开奖记录");
        System.out.println("  v          手动输入号码校验");
        System.out.println("  reload     重新拉取历史数据");
        System.out.println("  0          退出");
        System.out.println("\n  提示：单次最多生成 " + LotteryGenerator.MAX_BATCH_COUNT + " 注。");
    }

    private static List<Integer> parseNumbers(String input) {
        List<Integer> numbers = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) {
            return numbers;
        }
        for (String part : input.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                numbers.add(Integer.parseInt(part));
            }
        }
        return numbers;
    }

    private static int parsePositive(String s, int fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            int v = Integer.parseInt(s.trim());
            return v > 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
