package com.rbkmoney.webhook.dispatcher.listener;

import com.rbkmoney.webhook.dispatcher.Webhook;
import com.rbkmoney.webhook.dispatcher.dao.WebHookDao;
import com.rbkmoney.webhook.dispatcher.filter.DispatchFilter;
import com.rbkmoney.webhook.dispatcher.service.WebHookDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostponedWebHookListener {

    private final WebHookDispatcherService webHookDispatcherService;
    private final DispatchFilter postponedDispatchFilter;
    private final DispatchFilter timeDispatchFilter;
    private final WebHookDao webHookDao;
    private final KafkaTemplate<String, Webhook> kafkaTemplate;

    @Value("${kafka.topic.webhook.second.retry}")
    private String postponedTopic;

    @KafkaListener(topics = "${kafka.topic.webhook.first.retry}", containerFactory = "kafkaListenerContainerFactory")
    public void listen(String key, Webhook webhook, Acknowledgment acknowledgment) {
        log.info("WebHookListener webhook: {}", webhook);
        try {
            if (timeDispatchFilter.filter(webhook)) {
                if (postponedDispatchFilter.filter(webhook)) {
                    kafkaTemplate.send(postponedTopic, key, webhook);
                } else {
                    webHookDispatcherService.dispatch(new Webhook());
                    webHookDao.commit(webhook);
                }
                acknowledgment.acknowledge();
            }
            Thread.sleep(500L);
        } catch (Exception e) {
            log.error("Erro when listen webhook key: {} value: {} e: ", key, webhook, e);
        }
    }
}
