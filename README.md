# AMQ Streams CDC Demo

This project demonstrates a complete Change Data Capture (CDC) pipeline using Red Hat AMQ Streams (Kafka) on OpenShift. It captures changes from a MySQL database and replicates them to PostgreSQL using Debezium.

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│                 │     │                 │     │                 │     │                 │
│  Quarkus App    │────▶│     MySQL       │────▶│  Kafka Connect  │────▶│   PostgreSQL    │
│ (Data Producer) │     │   (Source DB)   │     │   (Debezium)    │     │  (Target DB)    │
│                 │     │                 │     │                 │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘     └─────────────────┘
                                                        │
                                                        ▼
                                                ┌─────────────────┐
                                                │                 │
                                                │  AMQ Streams    │
                                                │  (Kafka/KRaft)  │
                                                │                 │
                                                └─────────────────┘
```

## Components

| Component | Description |
|-----------|-------------|
| **AMQ Streams Operator** | Red Hat's Kafka distribution based on Strimzi |
| **Kafka Cluster** | Kafka cluster using KRaft mode (no ZooKeeper) |
| **MySQL** | Source database with binary logging enabled for CDC |
| **PostgreSQL** | Target database receiving replicated data |
| **Debezium MySQL Connector** | Captures changes from MySQL binlog |
| **JDBC Sink Connector** | Writes changes to PostgreSQL |
| **Quarkus Data Producer** | Generates test data at configurable intervals |

## Prerequisites

- OpenShift 4.14+ cluster
- `oc` CLI logged in with cluster-admin privileges
- Access to Red Hat registry (`registry.redhat.io`)
- OpenShift GitOps (ArgoCD) installed (for GitOps deployment)

## Quick Start - Deploy with Kustomize Overlay

The recommended way to deploy is using environment-specific overlays:

### Deploy Dev Environment

```bash
# Deploy everything in one command
oc apply -k overlays/dev

# Wait for the operator to install
oc wait --for=condition=AtLatestKnown subscription/dev-amq-streams -n amq-streams-cdc-dev --timeout=300s

# Wait for Kafka to be ready
oc wait kafka/dev-cdc-cluster --for=condition=Ready -n amq-streams-cdc-dev --timeout=600s

# Wait for KafkaConnect to be ready
oc wait kafkaconnect/dev-cdc-connect-cluster --for=condition=Ready -n amq-streams-cdc-dev --timeout=600s

# Trigger the data producer build
oc start-build dev-data-producer -n amq-streams-cdc-dev --follow
```

### Deploy Prod Environment

```bash
# Deploy everything
oc apply -k overlays/prod

# Follow similar wait commands with prod namespace and resource names
```

## GitOps Deployment with ArgoCD

### Option 1: Single Application

```bash
# Update the repoURL in argocd/application.yaml to your Git repository
# Then apply:
oc apply -f argocd/application.yaml
```

### Option 2: ApplicationSet (Multi-Environment)

```bash
# Update the repoURL in argocd/applicationset.yaml to your Git repository
# Then apply:
oc apply -f argocd/applicationset.yaml
```

This creates both `dev` and `prod` environments automatically.

## Configuration

### Data Producer Configuration

The data producer interval is configurable via ConfigMap:

```yaml
# In base/data-producer/configmap.yaml or via overlay
data:
  producer.interval: "60s"  # Default: 1 message per minute
```

Supported formats:
- `30s` - 30 seconds
- `1m` - 1 minute  
- `5m` - 5 minutes
- `500ms` - 500 milliseconds

### Environment-Specific Overrides

#### Development (`overlays/dev`)
- Single Kafka/KRaft replica
- 30-second data generation interval
- Ephemeral storage
- Resource names prefixed with `dev-`
- Namespace: `amq-streams-cdc-dev`

#### Production (`overlays/prod`)
- 3 Kafka replicas
- 60-second data generation interval
- Persistent storage (1GB PVCs for POC)

## Verifying the Pipeline

### 1. Check connector status

```bash
# Check MySQL source connector (dev environment)
oc get kafkaconnector dev-mysql-source-connector -n amq-streams-cdc-dev

# Check PostgreSQL sink connector
oc get kafkaconnector dev-postgres-sink-connector -n amq-streams-cdc-dev
```

### 2. View Kafka topics

```bash
# List topics (dev environment)
oc exec -it dev-cdc-cluster-dev-combined-0 -n amq-streams-cdc-dev -- \
  bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# View messages in the CDC topic
oc exec -it dev-cdc-cluster-dev-combined-0 -n amq-streams-cdc-dev -- \
  bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic dbserver1.inventory.products \
    --from-beginning \
    --max-messages 5
```

### 3. Query the databases

```bash
# Check MySQL source (dev environment)
oc exec -it deployment/dev-mysql -n amq-streams-cdc-dev -- \
  mysql -u debezium -pdbz inventory -e "SELECT * FROM products;"

# Check PostgreSQL target
oc exec -it deployment/dev-postgres -n amq-streams-cdc-dev -- \
  psql -U postgres -d inventory -c "SELECT * FROM products;"
```

### 4. Check data producer status

```bash
# Get the route (dev environment)
ROUTE=$(oc get route dev-data-producer -n amq-streams-cdc-dev -o jsonpath='{.spec.host}')

# Check health
curl -k https://$ROUTE/health

# Check status
curl -k https://$ROUTE/api/producer/status

# Manually trigger an insert
curl -k -X POST https://$ROUTE/api/producer/trigger
```

## Project Structure

```
amq-streams-cdc-demo/
├── README.md
├── argocd/
│   ├── application.yaml        # Single ArgoCD Application
│   └── applicationset.yaml     # Multi-env ApplicationSet
├── base/
│   ├── kustomization.yaml
│   ├── operator/               # AMQ Streams Operator
│   ├── kafka/                  # Kafka Cluster (KRaft mode)
│   ├── databases/              # MySQL & PostgreSQL
│   ├── kafka-connect/          # Debezium & JDBC Sink
│   └── data-producer/          # Quarkus App Deployment
├── overlays/
│   ├── dev/                    # Development overrides
│   └── prod/                   # Production overrides
└── quarkus-data-producer/      # Quarkus source code
```

## Troubleshooting

### Kafka Connect fails to start

Check if the operator has the necessary permissions to pull images:

```bash
oc get events -n amq-streams-cdc-dev --sort-by='.lastTimestamp'
```

### Connector not creating topics

Verify the Kafka cluster is ready:

```bash
oc get kafka dev-cdc-cluster -n amq-streams-cdc-dev
```

### MySQL CDC not capturing changes

Ensure binary logging is enabled:

```bash
oc exec -it deployment/dev-mysql -n amq-streams-cdc-dev -- \
  mysql -u root -prootpassword -e "SHOW VARIABLES LIKE 'log_bin';"
```

### Data not appearing in PostgreSQL

Check connector logs:

```bash
oc logs -l strimzi.io/cluster=dev-cdc-connect-cluster -n amq-streams-cdc-dev -c connect
```

## Cleanup

```bash
# Delete dev environment
oc delete -k overlays/dev

# Or delete the namespace entirely
oc delete namespace amq-streams-cdc-dev
```

## License

Apache License 2.0
