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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PackageStatusSubscriber implements AutoCloseable {

    private static final int ALERT_THRESHOLD = 5;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<DelayAlert> alerts = new CopyOnWriteArrayList<>();

    private final Connection connection;
    private final Session session;
    private final MessageConsumer consumer;

    public PackageStatusSubscriber() throws JMSException {
        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory(MqConfig.BROKER_URL);

        connection = factory.createConnection();
        connection.start();

        session = connection.createSession(
                false,
                Session.AUTO_ACKNOWLEDGE
        );

        Topic topic = session.createTopic("package-status-topic");
        consumer = session.createConsumer(topic);

        consumer.setMessageListener(message -> {
            if (!(message instanceof TextMessage textMessage)) {
                return;
            }

            try {
                PackageStatusEvent event = objectMapper.readValue(
                        textMessage.getText(),
                        PackageStatusEvent.class
                );

                System.out.printf(
                        "Received event: hub=%s, stage=%d%n",
                        event.hubId(),
                        event.stage()
                );

                if (event.stage() >= ALERT_THRESHOLD) {
                    createAlert(event);
                }

            } catch (Exception exception) {
                System.err.println(
                        "Could not process package-status event: "
                                + exception.getMessage()
                );
            }
        });
    }

    private void createAlert(PackageStatusEvent event) {
        String severity = event.stage() >= 7
                ? "CRITICAL"
                : "HIGH";

        DelayAlert alert = new DelayAlert(
                event.hubId(),
                event.stage(),
                severity,
                "Severe transit delay detected at hub " + event.hubId(),
                Instant.now().toString()
        );

        alerts.add(alert);

        System.out.printf(
                "ALERT [%s]: Hub %s reached delay stage %d%n",
                alert.severity(),
                alert.hubId(),
                alert.stage()
        );
    }

    public List<DelayAlert> getAlerts() {
        return List.copyOf(alerts);
    }

    public void clearAlerts() {
        alerts.clear();
    }

    @Override
    public void close() {
        try {
            consumer.close();
        } catch (JMSException ignored) {
        }

        try {
            session.close();
        } catch (JMSException ignored) {
        }

        try {
            connection.close();
        } catch (JMSException ignored) {
        }
    }
}
