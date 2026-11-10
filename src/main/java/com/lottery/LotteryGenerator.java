package com.lottery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 大乐透号码生成器
 * 前区：从 01-35 中随机选 5 个不重复号码
 * 后区：从 01-12 中随机选 2 个不重复号码
 */
public class LotteryGenerator {

    private static final int FRONT_MAX = 35;
    private static final int FRONT_COUNT = 5;
    private static final int BACK_MAX = 12;
    private static final int BACK_COUNT = 2;

    /** 单次批量生成的上限，防止误传超大值直接把内存吃光 */
    public static final int MAX_BATCH_COUNT = 1000;

    private final Random random;

    public LotteryGenerator() {
        this.random = new Random();
    }

    /** 指定随机种子的构造器，便于测试时复现结果 */
    public LotteryGenerator(long seed) {
        this.random = new Random(seed);
    }

    /** 生成一注随机号码 */
    public LotteryTicket generate() {
        List<Integer> front = pickUnique(FRONT_MAX, FRONT_COUNT);
        List<Integer> back = pickUnique(BACK_MAX, BACK_COUNT);
        return new LotteryTicket(front, back);
    }

    /** 批量生成多注号码 */
    public List<LotteryTicket> generate(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("生成注数必须大于 0，当前值：" + count);
        }
        if (count > MAX_BATCH_COUNT) {
            throw new IllegalArgumentException(
                    "单次最多生成 " + MAX_BATCH_COUNT + " 注，当前请求：" + count);
        }
        List<LotteryTicket> tickets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tickets.add(generate());
        }
        return tickets;
    }

    /** 校验一注号码是否合法 */
    public boolean validate(LotteryTicket ticket) {
        return ticket != null
                && validateFront(ticket.getFront())
                && validateBack(ticket.getBack());
    }

    private boolean validateFront(List<Integer> front) {
        return validate(front, FRONT_COUNT, FRONT_MAX);
    }

    private boolean validateBack(List<Integer> back) {
        return validate(back, BACK_COUNT, BACK_MAX);
    }

    private boolean validate(List<Integer> numbers, int expectedCount, int max) {
        if (numbers == null || numbers.size() != expectedCount) {
            return false;
        }
        for (Integer n : numbers) {
            if (n == null || n < 1 || n > max) {
                return false;
            }
        }
        // 不允许重复
        return numbers.stream().distinct().count() == expectedCount;
    }

    /** 从 1..max 中随机取 count 个不重复号码，升序返回 */
    private List<Integer> pickUnique(int max, int count) {
        List<Integer> pool = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            pool.add(i);
        }
        Collections.shuffle(pool, random);
        List<Integer> result = new ArrayList<>(pool.subList(0, count));
        Collections.sort(result);
        return result;
    }
}
