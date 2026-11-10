package com.lottery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一注大乐透号码：前区 5 个（01-35）+ 后区 2 个（01-12）。
 *
 * <p>不可变对象：构造时对入参做防御性拷贝，外部再改原 List 不会影响本对象；
 * 传入 null 会退化为空列表，因此 {@link #toString()} 不会 NPE。
 */
public class LotteryTicket {

    private final List<Integer> front;
    private final List<Integer> back;

    public LotteryTicket(List<Integer> front, List<Integer> back) {
        this.front = immutableCopy(front);
        this.back = immutableCopy(back);
    }

    public List<Integer> getFront() {
        return front;
    }

    public List<Integer> getBack() {
        return back;
    }

    private static List<Integer> immutableCopy(List<Integer> source) {
        if (source == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("前区: ");
        appendNumbers(sb, front);
        sb.append("    后区: ");
        appendNumbers(sb, back);
        return sb.toString();
    }

    private static void appendNumbers(StringBuilder sb, List<Integer> numbers) {
        for (int i = 0; i < numbers.size(); i++) {
            Integer n = numbers.get(i);
            sb.append(n == null ? "--" : String.format("%02d", n));
            if (i < numbers.size() - 1) {
                sb.append("  ");
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LotteryTicket)) {
            return false;
        }
        LotteryTicket other = (LotteryTicket) o;
        return front.equals(other.front) && back.equals(other.back);
    }

    @Override
    public int hashCode() {
        return 31 * front.hashCode() + back.hashCode();
    }
}
