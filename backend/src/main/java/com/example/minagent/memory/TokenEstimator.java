package com.example.minagent.memory;

public class TokenEstimator {

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long cjk = text.codePoints()
                .filter(cp -> {
                    Character.UnicodeScript script = Character.UnicodeScript.of(cp);
                    return script == Character.UnicodeScript.HAN;
                })
                .count();
        long nonCjk = text.length() - cjk;
        return Math.toIntExact(cjk + Math.ceilDiv(nonCjk, 4));
    }
}
