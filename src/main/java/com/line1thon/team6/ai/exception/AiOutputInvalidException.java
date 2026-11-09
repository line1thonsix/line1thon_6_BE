package com.line1thon.team6.ai.exception;

import java.util.List;

/** 응답이 출력 형식에 맞지 않음. problems는 다시 부를 때 수정 지시에 그대로 넣는다 */
public class AiOutputInvalidException extends RuntimeException {

    private final List<String> problems;

    public AiOutputInvalidException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }
}
