package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

public class PackageStatusPublisher
        implements AutoCloseable {

    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final ObjectMapper objectMapper;

    public PackageStatusPublisher() throws JMSException {
        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory(
                        MqConfig.BROKER_URL
                );

        connection = factory.createConnection();
        connection.start();

        session = connection.createSession(
                false,
                Session.AUTO_ACKNOWLEDGE
        );

        Topic topic = session.createTopic(MqConfig.TOPIC);
        producer = session.createProducer(topic);
        objectMapper = new ObjectMapper();
    }

    public void publish(PackageStatusEvent event)
            throws JMSException, JsonProcessingException {

        String json = objectMapper.writeValueAsString(event);
        TextMessage message =
                session.createTextMessage(json);

        producer.send(message);

        System.out.println(
                "Published package status event: " + json
        );
    }

    @Override
    public void close() {
        try {
            producer.close();
            session.close();
            connection.close();
        } catch (JMSException exception) {
            System.err.println(
                    "Could not close MQ publisher: " +
                            exception.getMessage()
            );
        }
    }
}
