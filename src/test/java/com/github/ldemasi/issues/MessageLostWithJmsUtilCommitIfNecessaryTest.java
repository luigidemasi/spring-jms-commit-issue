package com.github.ldemasi.issues;

import com.github.ldemasi.issues.support.CustomDoSendWithJmsUtilCommitJmsTemplate;
import com.github.ldemasi.issues.support.Response;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.Topic;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQTopic;
import org.assertj.core.api.Assertions;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.IllegalStateException;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageLostWithJmsUtilCommitIfNecessaryTest {

    private static final Logger LOG = LoggerFactory.getLogger(MessageLostWithJmsUtilCommitIfNecessaryTest.class);


    RestartAwareArtemisContainer broker;
    @Test
    public void messageLostTest() throws IOException, URISyntaxException, InterruptedException, JSONException {
        broker = new RestartAwareArtemisContainer("apache/activemq-artemis");
        broker.start();
        Topic topic = new ActiveMQTopic("artemis-topic-test");
        JmsTemplate template = jmsTemplate(topic);
        template.send(topic,s->s.createTextMessage("Hello!"));
        int actualMessagesCount = getMessageAdded();
        Assertions.assertThat(actualMessagesCount).isEqualTo(1);
    }

    private JmsTemplate jmsTemplate(Destination destination){
        CustomDoSendWithJmsUtilCommitJmsTemplate jmsTemplate = new CustomDoSendWithJmsUtilCommitJmsTemplate();
        jmsTemplate.setBroker(broker);
        jmsTemplate.setConnectionFactory(connectionFactory());
        jmsTemplate.setSessionTransacted(true);
        jmsTemplate.setDeliveryPersistent(true);
        jmsTemplate.setSessionAcknowledgeModeName("SESSION_TRANSACTED");
        jmsTemplate.setMessageIdEnabled(true);
        jmsTemplate.setMessageTimestampEnabled(true);
        jmsTemplate.setDefaultDestination(destination);
        return jmsTemplate;
    }

    private int getMessageAdded() throws URISyntaxException, IOException, InterruptedException, JSONException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(
                        "http://"+broker.getHost()+":8161/console/jolokia/read/org.apache.activemq.artemis:broker=!%220.0.0.0!%22,component=addresses,address=!%22artemis-demo-topic!%22,subcomponent=queues,routing-type=!%22multicast!%22,queue=!%22sub1-artemis-demo-topic!%22/MessagesAdded"))
                .GET()
                .header("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString((broker.getUser() + ":" + broker.getPassword()).getBytes()))
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isLessThan(300);
        LOG.info("Jolokia Response: ErrorCode: {} Body: {}", response.statusCode(), response.body());
        ObjectMapper mapper = new ObjectMapper();
        Response queueAttribureResponse = mapper.readValue(response.body(), Response.class);
        int value = queueAttribureResponse.getValue();
        assertThat(value).isNotNull();
        return value;
    }

    private ConnectionFactory connectionFactory(){
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(broker.getBrokerUrl());
        cf.setUser(broker.getUser());
        cf.setPassword(broker.getPassword());
        connectionFactory.setTargetConnectionFactory(cf);
        return connectionFactory;
    }

}
