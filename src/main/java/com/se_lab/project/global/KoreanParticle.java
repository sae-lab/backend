package com.se_lab.project.global;

/// 한글 조사를 앞 글자의 받침에 맞춰 고른다.
///
/// 도시 이름을 문장에 끼워 넣을 때 조사를 하나로 고정하면
/// "삼척를 잇는"처럼 어색해진다. 받침이 있으면 을/은/과, 없으면 를/는/와다.
public final class KoreanParticle {

    private static final char HANGUL_START = 0xAC00;
    private static final char HANGUL_END = 0xD7A3;
    private static final int JONGSEONG_COUNT = 28;

    private KoreanParticle() {
    }

    /// 목적격 조사. "삼척" -> "을", "동해" -> "를"
    public static String objective(String word) {
        return hasFinalConsonant(word) ? "을" : "를";
    }

    /// 주격/보조사. "삼척" -> "은", "동해" -> "는"
    public static String topic(String word) {
        return hasFinalConsonant(word) ? "은" : "는";
    }

    /// 앞말에 조사를 붙여 돌려준다. objective("삼척") -> "삼척을"
    public static String withObjective(String word) {
        return word + objective(word);
    }

    /// 마지막 글자에 받침이 있는지.
    ///
    /// 한글이 아닌 글자(영문·숫자 등)로 끝나면 판단할 수 없으므로 받침이 없는
    /// 것으로 본다. 조사가 조금 어색해질 수는 있어도 예외로 죽지는 않는다.
    static boolean hasFinalConsonant(String word) {
        if (word == null || word.isBlank()) return false;

        char last = word.charAt(word.length() - 1);
        if (last < HANGUL_START || last > HANGUL_END) return false;

        return (last - HANGUL_START) % JONGSEONG_COUNT != 0;
    }
}
