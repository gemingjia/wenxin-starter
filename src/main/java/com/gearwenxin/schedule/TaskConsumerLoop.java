package com.gearwenxin.schedule;

import com.gearwenxin.config.ModelConfig;
import com.gearwenxin.entity.chatmodel.ChatPromptRequest;
import com.gearwenxin.entity.response.PromptResponse;
import com.gearwenxin.schedule.entity.ChatTask;
import com.gearwenxin.service.ChatService;
import com.gearwenxin.service.ImageService;
import com.gearwenxin.entity.chatmodel.ChatBaseRequest;
import com.gearwenxin.entity.chatmodel.ChatErnieRequest;
import com.gearwenxin.entity.request.ImageBaseRequest;
import com.gearwenxin.entity.response.ChatResponse;
import com.gearwenxin.entity.response.ImageResponse;
import com.gearwenxin.service.PromptService;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
public class TaskConsumerLoop {

    public static final String TAG = "TaskConsumerLoop";
    public static final int DEFAULT_QPS = -1;

    @Getter
    @Setter
    private List<String> modelQPSList = null;

    @Resource
    private ChatService chatService;
    @Resource
    private PromptService promptService;
    @Resource
    private ImageService imageService;

    private static final Map<String, Integer> modelQPSMap = new HashMap<>();

    private final TaskQueueManager taskManager = TaskQueueManager.getInstance();

    public void start() {
        initModelQPSMap();
        Set<String> modelNames = modelQPSMap.keySet();
        modelNames.forEach(modelName -> new Thread(() -> {
            try {
                Thread.currentThread().setName(modelName + "-thread");
                log.info("[{}] {}, model: {}, loop start", TAG, Thread.currentThread().getName(), modelName);
                eventLoopProcess(modelName);
            } catch (Exception e) {
                log.error("[{}] loop-process error, modelName: {}, thread-{}", TAG, modelName, Thread.currentThread().getName(), e);
                if (!Thread.currentThread().isAlive()) {
                    log.error("[{}] {} is not alive", TAG, Thread.currentThread().getName());
                    log.info("[{}] restarting model: {}", TAG, modelName);
                    Thread.currentThread().start();
                }
            }
        }).start());
    }

    public void initModelQPSMap() {
        if (modelQPSList == null || modelQPSList.isEmpty()) {
            return;
        }
        log.debug("[{}] model qps list: {}", TAG, modelQPSList);
        modelQPSList.forEach(s -> {
            String[] split = s.split(" ");
            modelQPSMap.put(split[0], Integer.parseInt(split[1]));
        });
        log.info("[{}] init model qps map complete", TAG);
    }

    private int getModelQPS(String modelName) {
        return modelQPSMap.getOrDefault(modelName, DEFAULT_QPS);
    }

    public void eventLoopProcess(String modelName) {
        Map<String, Integer> modelCurrentQPSMap = taskManager.getModelCurrentQPSMap();
        while (true) {
            Integer currentQPS = modelCurrentQPSMap.get(modelName);
            if (currentQPS == null) {
                taskManager.initModelCurrentQPS(modelName);
                currentQPS = 0;
            }
            log.debug("[{}] [{}] current qps: {}", TAG, modelName, currentQPS);
            if (currentQPS < getModelQPS(modelName) || getModelQPS(modelName) == DEFAULT_QPS) {
                ChatTask task = taskManager.getTask(modelName);
                if (task == null) {
                    try {
                        log.debug("[{}] model: {}, sleep 1s", TAG, modelName);
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        log.error("[{}] thread sleep error", TAG, e);
                    }
                    continue;
                }
                log.debug("[{}] get task: {}", TAG, task);
                submitTask(task);
                taskManager.upModelCurrentQPS(modelName);
            } else {
                // TODO: 待优化，QPS超额应该直接wait()
                try {
                    log.debug("[{}] model: {}, sleep 1s", TAG, modelName);
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error("[{}] thread sleep error", TAG, e);
                }
            }
        }

    }

    private void submitTask(ChatTask task) {
        String taskId = task.getTaskId();
        ModelConfig modelConfig = task.getModelConfig();
        ExecutorService executorService = ThreadPoolManager.getInstance(task.getTaskType());
        switch (task.getTaskType()) {
            case chat -> {
                CompletableFuture<Publisher<ChatResponse>> completableFuture = CompletableFuture.supplyAsync(() -> {
                    // 如果包含ernie，则使用erni的请求类
                    ChatBaseRequest taskRequest;
                    if (modelConfig.getModelName().toLowerCase().contains("ernie")) {
                        taskRequest = (ChatErnieRequest) task.getTaskRequest();
                    } else {
                        taskRequest = (ChatBaseRequest) task.getTaskRequest();
                    }
                    log.debug("[{}] submit task {}, ernie: {}", TAG, taskId, taskRequest.getClass() == ChatErnieRequest.class);
                    return chatService.chatProcess(taskRequest, task.getMessageId(), task.isStream(), modelConfig);
                }, executorService);
                taskManager.getChatFutureMap().putAndNotify(taskId, completableFuture);
            }
            case prompt -> {
                CompletableFuture<Mono<PromptResponse>> completableFuture = CompletableFuture.supplyAsync(() -> {
                    log.debug("[{}] submit task {}, type: prompt", TAG, taskId);
                    return promptService.promptProcess((ChatPromptRequest) task.getTaskRequest(), modelConfig);
                }, executorService);
                taskManager.getPromptFutureMap().putAndNotify(taskId, completableFuture);
            }
            case image -> {
                CompletableFuture<Mono<ImageResponse>> completableFuture = CompletableFuture.supplyAsync(() -> {
                    log.debug("[{}] submit task {}, type: image", TAG, taskId);
                    return imageService.imageProcess((ImageBaseRequest) task.getTaskRequest(), modelConfig);
                }, executorService);
                taskManager.getImageFutureMap().putAndNotify(taskId, completableFuture);
                log.debug("[{}] add a image task, taskId: {}", TAG, taskId);
            }
        }
    }

}