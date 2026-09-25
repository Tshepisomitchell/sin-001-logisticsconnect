package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import javax.jms.JMSException;
import java.util.Map;

public class AlertBotApp {

    private static final int PORT = 7054;

    public static void main(String[] args) {
        PackageStatusSubscriber subscriber;

        try {
            subscriber = new PackageStatusSubscriber();
        } catch (JMSException exception) {
            System.err.println(
                    "Could not connect AlertBot to ActiveMQ: "
                            + exception.getMessage()
            );
            return;
        }

        Runtime.getRuntime().addShutdownHook(
                new Thread(subscriber::close)
        );

        Javalin app = Javalin.create().start(PORT);

        app.get("/health", context ->
                context.result("OK")
        );

        app.get("/alerts", context ->
                context.json(subscriber.getAlerts())
        );

        app.delete("/alerts", context -> {
            subscriber.clearAlerts();

            context.json(Map.of(
                    "message", "Alerts cleared"
            ));
        });

        System.out.println(
                "AlertBot service listening on port " + PORT
        );
    }
}