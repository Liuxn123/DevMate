package com.devmate.agent.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devmate.agent.dto.AgentEvent;

public final class AgentTrace {

    // 保存一条消息从模型请求、工具调用到最终答复的过程，便于界面展示“Agent 做了什么”。
    private final List<AgentEvent> events = new ArrayList<>();
    // 轮数限制和工具调用次数限制是两件事，因此分别计数。
    private final int maxToolCalls;
    private int toolCalls;
    private int currentRound;

    public AgentTrace(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public synchronized void beginRound(int round) {
        currentRound = round;
        add("MODEL_REQUEST", null, "向模型发送第 " + round + " 轮请求", Map.of());
    }

    public synchronized void modelRequestedTools(int round) {
        currentRound = round;
        add("MODEL_DECISION", null, "模型请求调用只读工具", Map.of());
    }

    public synchronized boolean beginToolCall(String toolName, Map<String, Object> arguments) {
        // 在工具真正执行前拦截超限调用，并留下拒绝事件。
        if (toolCalls >= maxToolCalls) {
            add("TOOL_REJECTED", toolName, "达到工具调用次数上限，未执行此调用", Map.of("limit", maxToolCalls));
            return false;
        }
        toolCalls++;
        add("TOOL_CALL", toolName, "调用只读工具", arguments);
        return true;
    }

    public synchronized void toolResult(String toolName, Object result) {
        add("TOOL_RESULT", toolName, "只读工具返回结果", Map.of("result", result));
    }

    public synchronized void toolFailure(String toolName, String message) {
        add("TOOL_ERROR", toolName, "只读工具调用失败", Map.of("message", message));
    }

    public synchronized void loopLimitReached(int rounds) {
        add("LOOP_LIMIT", null, "达到工具调用轮数上限", Map.of("maxRounds", rounds));
    }

    public synchronized void modelAnswered() {
        add("MODEL_RESPONSE", null, "模型生成最终答复", Map.of());
    }

    public synchronized List<AgentEvent> snapshot() {
        // 返回不可修改的副本，避免响应发出后外部继续改变这份过程记录。
        return List.copyOf(events);
    }

    public synchronized int toolCalls() {
        return toolCalls;
    }

    private void add(String type, String toolName, String summary, Map<String, Object> details) {
        events.add(new AgentEvent(events.size() + 1, currentRound, type, toolName, summary,
                Map.copyOf(new LinkedHashMap<>(details))));
    }
}
