package com.github.ldemasi.issues.support;

import com.github.ldemasi.issues.RestartAwareArtemisContainer;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.support.JmsUtils;
import org.springframework.util.Assert;

public class CustomDoSendWithJmsUtilCommitJmsTemplate extends JmsTemplate {

    RestartAwareArtemisContainer broker;

    public RestartAwareArtemisContainer getBroker() {
        return broker;
    }

    public void setBroker(RestartAwareArtemisContainer broker) {
        this.broker = broker;
    }

    /**
     * Send the given JMS message.
     * @param session the JMS Session to operate on
     * @param destination the JMS Destination to send to
     * @param messageCreator callback to create a JMS Message
     * @throws JMSException if thrown by JMS API methods
     */
    @Override
    protected void doSend(Session session, Destination destination, MessageCreator messageCreator) throws JMSException {

        Assert.notNull(messageCreator, "MessageCreator must not be null");
        MessageProducer producer = createProducer(session, destination);
        try {
            Message message = messageCreator.createMessage(session);
            if (logger.isDebugEnabled()) {
                logger.debug("Sending created message: " + message);
            }
            doSend(producer, message);
            // Check commit - avoid commit call within a JTA transaction.
            if (session.getTransacted() && isSessionLocallyTransacted(session)) {
                broker.restart();
                // Transacted session created by this template -> commit.
                JmsUtils.commitIfNecessary(session);
                //session.commit();
            }
        }
        finally {
            JmsUtils.closeMessageProducer(producer);
        }
    }

}
