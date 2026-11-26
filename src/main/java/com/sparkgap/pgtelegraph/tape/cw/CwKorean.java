package com.sparkgap.pgtelegraph.tape.cw;

import com.sparkgap.pgtelegraph.tape.Morse;

import java.util.List;
import java.util.Set;

/**
 * Korean (한글) Morse lexicon for the beam decoder. Two things make a reading of Korean code
 * plausible:
 * <ol>
 *   <li>it composes into real syllables - every vowel has its initial consonant (ㅇ included),
 *       consonants do not pile up beyond a final cluster - which the strict shape of Hangul makes
 *       a strong check: a split or merged jamo almost always leaves a stray one behind</li>
 *   <li>it is a word radio operators and players actually send (a word list with the common
 *       particles allowed after it)</li>
 * </ol>
 */
public final class CwKorean {
    private CwKorean() {}

    private static final Set<String> WORDS = Set.of(
            // greetings, procedure
            "안녕", "안녕하세요", "안녕하십니까", "반갑습니다", "반가워", "감사합니다", "고맙습니다", "고마워", "수고",
            "수고하세요", "수고하셨습니다", "잘가", "또봐", "다음에", "네", "예", "아니요", "아니", "알겠습니다", "알겠다",
            "알았어", "확인", "확인했습니다", "수신", "송신", "교신", "감도", "양호", "불량", "약함", "강함", "잡음",
            "다시", "반복", "천천히", "빨리", "대기", "끝", "이상", "오버", "아웃", "들립니까", "들려요", "들립니다",
            "잘", "안", "못", "호출", "호출부호", "무선국", "무전", "무전기", "전신", "모스", "부호", "주파수", "안테나",
            "출력", "전원", "배터리", "시험", "테스트", "응답", "응답하라", "여기는", "여기", "거기", "저기",
            // people, places, time
            "나", "너", "저", "우리", "친구", "이름", "기지", "집", "마을", "도시", "성", "탑", "광산", "농장", "항구",
            "좌표", "위치", "북쪽", "남쪽", "동쪽", "서쪽", "북", "남", "동", "서", "위", "아래", "지금", "오늘", "내일",
            "어제", "아침", "점심", "저녁", "밤", "낮", "시", "분", "날씨", "비", "눈", "맑음", "흐림", "바람", "천둥",
            // game
            "스폰", "네더", "엔더", "다이아", "다이아몬드", "철", "금", "구리", "석탄", "레드스톤", "엘리트라", "포탈",
            "좀비", "크리퍼", "스켈레톤", "드래곤", "몹", "상자", "작업대", "화로", "음식", "빵", "고기",
            // requests
            "도와줘", "도와주세요", "도움", "구조", "위험", "조심", "와", "와라", "와주세요", "가", "간다", "갑니다",
            "온다", "옵니다", "있다", "있어", "있습니다", "없다", "없어", "없습니다", "좋아", "좋습니다", "좋다",
            "만나", "만나자", "만납시다", "기다려", "기다려주세요", "출발", "도착", "준비", "완료", "필요", "주세요",
            // numbers in words
            "하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "백", "천", "만");

    private static final String[] PARTICLES = {"은", "는", "이", "가", "을", "를", "에", "에서", "로", "으로", "와", "과",
            "도", "만", "의", "요", "까지", "부터", "한테", "께"};

    /** log score of one Korean word given as Morse codes (0 = fine, negative = odd) */
    public static double wordScore(List<String> codes) {
        if (codes.isEmpty()) return 0;
        String w = Morse.toKorean(codes);
        int stray = 0, syl = 0;
        for (char c : w.toCharArray()) {
            if (c >= 0x3131 && c <= 0x318E) stray++;          // a jamo that did not fit a syllable
            else if (c >= 0xAC00 && c <= 0xD7A3) syl++;
            else if (c == '*') stray += 2;
        }
        double s = -2.5 * stray;
        if (syl == 0) return s - 1.5;
        if (stray == 0 && known(w)) return s;
        return s - 1.0; // a real-looking word that is not in the list (names, places...)
    }

    private static boolean known(String w) {
        if (WORDS.contains(w)) return true;
        for (String p : PARTICLES)
            if (w.length() > p.length() && w.endsWith(p) && WORDS.contains(w.substring(0, w.length() - p.length()))) return true;
        return w.chars().allMatch(Character::isDigit);
    }
}
