package com.redhat.demo.producer;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Scheduled job that produces data at a configurable interval.
 * 
 * The interval is configured via the 'producer.interval' property,
 * which accepts standard duration format (e.g., "1m", "30s", "500ms").
 * 
 * Default interval is 1 minute (60s).
 */
@ApplicationScoped
public class ScheduledProducer {

    private static final Logger LOG = Logger.getLogger(ScheduledProducer.class);

    @Inject
    DataProducerService dataProducerService;

    @ConfigProperty(name = "producer.interval", defaultValue = "60s")
    String interval;

    /**
     * Scheduled method that runs at the configured interval.
     * The interval is controlled by the 'producer.interval' configuration property.
     */
    @Scheduled(every = "${producer.interval:60s}", identity = "data-producer-job")
    void produceScheduledData() {
        LOG.debugf("Scheduled producer triggered (interval: %s)", interval);
        Product product = dataProducerService.produceData();
        
        if (product != null) {
            LOG.infof("Scheduled insert completed: %s", product.getName());
        }
    }
}

