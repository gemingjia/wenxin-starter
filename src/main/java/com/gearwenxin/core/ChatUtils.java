package com.gearwenxin.core;

import com.gearwenxin.entity.Message;
import com.gearwenxin.entity.enums.Role;

import java.util.Deque;
import java.util.LinkedList;

import static com.gearwenxin.common.Constant.MAX_TOTAL_LENGTH;
import static com.gearwenxin.common.WenXinUtils.assertNotBlank;
import static com.gearwenxin.common.WenXinUtils.assertNotNull;

public class ChatUtils {

    /**
     * 向历史消息中添加消息
     *
     * @param originalHistory 历史消息队列
     * @param message         需添加的Message
     */
    public static void addMessage(Deque<Message> originalHistory, Message message) {
        assertNotNull(originalHistory, "messagesHistory is null");
        assertNotNull(message, "message is null");
        assertNotBlank(message.getContent(), "message.content is null or blank");

        // 复制原始历史记录，避免直接修改原始历史记录
        Deque<Message> updatedHistory = new LinkedList<>(originalHistory);

        // 验证消息规则
        validateMessageRule(updatedHistory, message);

        // 将新消息添加到历史记录中
        updatedHistory.offer(message);

        if (message.getRole() == Role.assistant) {
            synchronizeHistories(originalHistory, updatedHistory);
            return;
        }

        // 处理超出长度的情况
        handleExceedingLength(updatedHistory);

        // 同步历史记录
        synchronizeHistories(originalHistory, updatedHistory);
    }

    private static void validateMessageRule(Deque<Message> history, Message message) {
        if (!history.isEmpty()) {
            Message lastMessage = history.peekLast();
            if (lastMessage != null) {
                // 如果当前是奇数位message，要求role值为user或function
                if (history.size() % 2 != 0) {
                    if (message.getRole() != Role.user && message.getRole() != Role.function) {
                        // 删除最后一条消息
                        history.pollLast();
                        validateMessageRule(history, message);
                    }
                } else {
                    // 如果当前是偶数位message，要求role值为assistant
                    if (message.getRole() != Role.assistant) {
                        // 删除最后一条消息
                        history.pollLast();
                        validateMessageRule(history, message);
                    }
                }

                // 第一个message的role不能是function
                if (history.size() == 1 && message.getRole() == Role.function) {
                    // 删除最后一条消息
                    history.pollLast();
                    validateMessageRule(history, message);
                }

                // 移除连续的相同role的user messages
                if (lastMessage.getRole() == Role.user && message.getRole() == Role.user) {
                    history.pollLast();
                    validateMessageRule(history, message);
                }
            }
        }
    }

    private static void synchronizeHistories(Deque<Message> original, Deque<Message> updated) {
        if (updated.size() <= original.size()) {
            updated.clear();
            updated.addAll(original);
        }
    }

    private static void handleExceedingLength(Deque<Message> updatedHistory) {
        int totalLength = updatedHistory.stream()
                .filter(msg -> msg.getRole() == Role.user)
                .mapToInt(msg -> msg.getContent().length())
                .sum();

        while (totalLength > MAX_TOTAL_LENGTH && updatedHistory.size() > 2) {
            Message firstMessage = updatedHistory.poll();
            Message secondMessage = updatedHistory.poll();
            if (firstMessage != null && secondMessage != null) {
                totalLength -= (firstMessage.getContent().length() + secondMessage.getContent().length());
            } else if (secondMessage != null) {
                updatedHistory.addFirst(secondMessage);
                totalLength -= secondMessage.getContent().length();
            }
        }
    }

}
