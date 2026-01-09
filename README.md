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
                                                │    (Kafka)      │
                                                │                 │
                                                └─────────────────┘
```

## Components

| Component | Description |
|-----------|-------------|
| **AMQ Streams Operator** | Red Hat's Kafka distribution based on Strimzi |
| **Kafka Cluster** | 3-node Kafka cluster with Zookeeper |
| **MySQL** | Source database with binary logging enabled for CDC |
| **PostgreSQL** | Target database receiving replicated data |
| **Debezium MySQL Connector** | Captures changes from MySQL binlog |
| **Camel JDBC Sink Connector** | Writes changes to PostgreSQL |
| **Quarkus Data Producer** | Generates test data at configurable intervals |

## Prerequisites

- OpenShift 4.12+ cluster
- `oc` CLI logged in with cluster-admin privileges
- Access to Red Hat registry (`registry.redhat.io`)
- OpenShift GitOps (ArgoCD) installed (for GitOps deployment)

## Quick Start - Manual Deployment

### 1. Create the namespace and install AMQ Streams operator

```bash
# Apply operator resources
oc apply -k base/operator

# Wait for the operator to be ready
oc wait --for=condition=Ready pod -l name=amq-streams-cluster-operator -n amq-streams-cdc --timeout=300s
```

### 2. Deploy the Kafka cluster

```bash
# Apply Kafka resources
oc apply -k base/kafka

# Wait for Kafka to be ready
oc wait kafka/cdc-cluster --for=condition=Ready -n amq-streams-cdc --timeout=600s
```

### 3. Deploy the databases

```bash
# Apply database resources
oc apply -k base/databases

# Wait for databases to be ready
oc wait --for=condition=Available deployment/mysql -n amq-streams-cdc --timeout=300s
oc wait --for=condition=Available deployment/postgres -n amq-streams-cdc --timeout=300s
```

### 4. Deploy Kafka Connect with connectors

```bash
# Apply Kafka Connect resources
oc apply -k base/kafka-connect

# Wait for Kafka Connect to be ready
oc wait kafkaconnect/cdc-connect-cluster --for=condition=Ready -n amq-streams-cdc --timeout=600s
```

### 5. Build and deploy the Quarkus data producer

```bash
# Apply data producer resources
oc apply -k base/data-producer

# Start the build
oc start-build data-producer -n amq-streams-cdc --follow

# Wait for deployment
oc wait --for=condition=Available deployment/data-producer -n amq-streams-cdc --timeout=300s
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
- Single Kafka replica
- 30-second data generation interval
- Ephemeral storage

#### Production (`overlays/prod`)
- 3 Kafka replicas
- 60-second data generation interval
- Persistent storage (50GB Kafka, 10GB databases)

## Verifying the Pipeline

### 1. Check connector status

```bash
# Check MySQL source connector
oc get kafkaconnector mysql-source-connector -n amq-streams-cdc -o yaml

# Check PostgreSQL sink connector
oc get kafkaconnector postgres-sink-connector -n amq-streams-cdc -o yaml
```

### 2. View Kafka topics

```bash
# List topics
oc exec -it cdc-cluster-kafka-0 -n amq-streams-cdc -- \
  bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# View messages in the CDC topic
oc exec -it cdc-cluster-kafka-0 -n amq-streams-cdc -- \
  bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic dbserver1.inventory.products \
    --from-beginning \
    --max-messages 5
```

### 3. Query the databases

```bash
# Check MySQL source
oc exec -it deployment/mysql -n amq-streams-cdc -- \
  mysql -u debezium -pdbz inventory -e "SELECT * FROM products;"

# Check PostgreSQL target
oc exec -it deployment/postgres -n amq-streams-cdc -- \
  psql -U postgres -d inventory -c "SELECT * FROM products;"
```

### 4. Check data producer status

```bash
# Get the route
ROUTE=$(oc get route data-producer -n amq-streams-cdc -o jsonpath='{.spec.host}')

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
│   │   ├── kustomization.yaml
│   │   ├── namespace.yaml
│   │   ├── operatorgroup.yaml
│   │   └── subscription.yaml
│   ├── kafka/                  # Kafka Cluster
│   │   ├── kustomization.yaml
│   │   ├── kafka-cluster.yaml
│   │   └── kafka-topics.yaml
│   ├── databases/              # MySQL & PostgreSQL
│   │   ├── kustomization.yaml
│   │   ├── mysql-*.yaml
│   │   └── postgres-*.yaml
│   ├── kafka-connect/          # Debezium & JDBC Sink
│   │   ├── kustomization.yaml
│   │   ├── kafka-connect.yaml
│   │   ├── mysql-source-connector.yaml
│   │   └── postgres-sink-connector.yaml
│   └── data-producer/          # Quarkus App Deployment
│       ├── kustomization.yaml
│       ├── buildconfig.yaml
│       ├── configmap.yaml
│       └── deployment.yaml
├── overlays/
│   ├── dev/                    # Development overrides
│   │   ├── kustomization.yaml
│   │   └── namespace.yaml
│   └── prod/                   # Production overrides
│       ├── kustomization.yaml
│       └── pvcs.yaml
└── quarkus-data-producer/      # Quarkus source code
    ├── pom.xml
    └── src/
```

## Troubleshooting

### Kafka Connect fails to start

Check if the operator has the necessary permissions to pull images:

```bash
oc get events -n amq-streams-cdc --sort-by='.lastTimestamp'
```

### Connector not creating topics

Verify the Kafka cluster is ready:

```bash
oc get kafka cdc-cluster -n amq-streams-cdc
```

### MySQL CDC not capturing changes

Ensure binary logging is enabled:

```bash
oc exec -it deployment/mysql -n amq-streams-cdc -- \
  mysql -u root -prootpassword -e "SHOW VARIABLES LIKE 'log_bin';"
```

### Data not appearing in PostgreSQL

Check connector logs:

```bash
oc logs -l strimzi.io/cluster=cdc-connect-cluster -n amq-streams-cdc -c connect
```

## Cleanup

```bash
# Delete all resources
oc delete -k overlays/prod  # or overlays/dev

# Or delete the namespace entirely
oc delete namespace amq-streams-cdc
```

## License

Apache License 2.0

