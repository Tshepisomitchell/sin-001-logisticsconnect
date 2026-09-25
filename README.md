# LogisticsConnect

LogisticsConnect is a Java microservices system that cleans logistics hub data, exposes it through REST APIs, calculates transit estimates, and uses ActiveMQ for event-driven delay notifications.

## Architecture

The project contains five services:

| Service | Port | Responsibility |
|---|---:|---|
| Ingestion Service | 7050 | Cleans and exposes logistics hub data |
| Hub Service | 7051 | Provides hub and province APIs |
| Delay Stage Service | 7052 | Stores delay stages and publishes events |
| Transit Service | 7053 | Calculates delivery estimates using hub and delay data |
| AlertBot | 7054 | Generates alerts for serious transit delays |

Apache ActiveMQ runs on port `61616`. Delay Stage publishes package-status events to the `package-status-topic`. Transit Service and AlertBot subscribe to that topic.

## Technologies

- Java 17
- Maven
- Javalin
- Jackson
- Apache ActiveMQ
- JMS
- Docker Compose
- REST APIs

## Running ActiveMQ

From the repository root:

```powershell
cd common
docker compose up -d
docker compose ps
cd ..
```

The ActiveMQ console is available at `http://localhost:8161`.

Default credentials:

```text
Username: admin
Password: admin
```

## Building the Services

Run these commands from the repository root:

```powershell
mvn -f ingestion-service\pom.xml clean package
mvn -f hub-service\pom.xml clean package
mvn -f delay-stage-service\pom.xml clean package
mvn -f transit-service\pom.xml clean package
mvn -f alertbot\pom.xml clean package
```

## Running the Services

Run each service in a separate terminal:

```powershell
java -jar ingestion-service\target\ingestion-service.jar
java -jar hub-service\target\hub-service.jar
java -jar transit-service\target\transit-service.jar
java -jar alertbot\target\alertbot.jar
java -jar delay-stage-service\target\delay-stage-service.jar
```

Start Transit Service and AlertBot before publishing events because ActiveMQ topic messages are not retained for inactive subscribers.

## Example Event Flow

Publish a delay stage:

```powershell
$body = @{ stage = 7 } | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:7052/delay-stage/H-500" `
    -ContentType "application/json" `
    -Body $body
```

Retrieve the transit estimate:

```powershell
Invoke-RestMethod -Uri "http://localhost:7053/transit/H-500/eta"
```

Retrieve generated alerts:

```powershell
Invoke-RestMethod -Uri "http://localhost:7054/alerts"
```

A stage `7` event generates a `CRITICAL` alert.

The transit estimate uses:

```text
Estimated minutes = 60 + (delay stage × 30)
```

## API Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Check service health |
| GET | `/hubs` | Retrieve all hubs |
| GET | `/hubs/{hubId}` | Retrieve one hub |
| GET | `/provinces` | Retrieve available provinces |
| GET | `/provinces/{province}/hubs` | Retrieve hubs by province |
| POST | `/refresh` | Refresh hub data |
| GET | `/delay-stage/{hubId}` | Retrieve a hub's delay stage |
| POST | `/delay-stage/{hubId}` | Update and publish a delay stage |
| GET | `/transit/{hubId}/eta` | Retrieve a transit estimate |
| GET | `/alerts` | Retrieve generated alerts |
| DELETE | `/alerts` | Clear generated alerts |

## Validation and Error Handling

The services handle:

- Invalid and incomplete CSV records
- Unknown hubs
- Inactive hubs
- Invalid delay stages
- Invalid request bodies
- Missing delay events
- ActiveMQ connection and publishing failures

## What I Learned

This project taught me how to integrate Java microservices using synchronous REST calls and asynchronous message-based communication. I learned how to clean inconsistent CSV data, design REST contracts, use ActiveMQ topics with JMS, calculate transit estimates, and handle failures across multiple services.