package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PackageStatusSubscriber
        implements AutoCloseable {

    private final Map<String, Integer> stages =
            new ConcurrentHashMap<>();

    private final Connection connection;
    private final Session session;
    private final MessageConsumer consumer;
    private final ObjectMapper objectMapper =
            new ObjectMapper();

    public PackageStatusSubscriber() throws JMSException {
        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory(
                        MqConfig.BROKER_URL
                );

        connection = factory.createConnection();

        session = connection.createSession(
                false,
                Session.AUTO_ACKNOWLEDGE
        );

        Topic topic = session.createTopic(MqConfig.TOPIC);
        consumer = session.createConsumer(topic);

        consumer.setMessageListener(message -> {
            if (!(message instanceof TextMessage textMessage)) {
                return;
            }

            try {
                PackageStatusEvent event =
                        objectMapper.readValue(
                                textMessage.getText(),
                                PackageStatusEvent.class
                        );

                stages.put(
                        event.hubId().toUpperCase(),
                        event.stage()
                );

                System.out.println(
                        "Received package status event: " +
                                textMessage.getText()
                );

            } catch (Exception exception) {
                System.err.println(
                        "Could not process MQ message: " +
                                exception.getMessage()
                );
            }
        });

        connection.start();
    }

    public Integer getStage(String hubId) {
        return stages.get(hubId.toUpperCase());
    }

    @Override
    public void close() {
        try {
            consumer.close();
            session.close();
            connection.close();
        } catch (JMSException exception) {
            System.err.println(
                    "Could not close MQ subscriber: " +
                            exception.getMessage()
            );
        }
    }
}
