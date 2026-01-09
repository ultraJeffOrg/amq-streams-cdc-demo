package com.redhat.demo.producer;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

/**
 * REST API for manually triggering data production and checking status.
 */
@Path("/api/producer")
@Produces(MediaType.APPLICATION_JSON)
public class ProducerResource {

    @Inject
    DataProducerService dataProducerService;

    @ConfigProperty(name = "producer.interval", defaultValue = "60s")
    String interval;

    /**
     * Get the current status of the data producer.
     */
    @GET
    @Path("/status")
    public Response getStatus() {
        Map<String, Object> status = Map.of(
                "enabled", dataProducerService.isEnabled(),
                "interval", interval,
                "insertCount", dataProducerService.getInsertCount(),
                "errorCount", dataProducerService.getErrorCount(),
                "totalProductsInDb", dataProducerService.getProductCount()
        );
        return Response.ok(status).build();
    }

    /**
     * Manually trigger a data insert (bypasses scheduler).
     */
    @POST
    @Path("/trigger")
    public Response triggerInsert() {
        Product product = dataProducerService.produceData();
        
        if (product != null) {
            return Response.ok(Map.of(
                    "success", true,
                    "message", "Product inserted successfully",
                    "product", Map.of(
                            "id", product.getId(),
                            "name", product.getName(),
                            "description", product.getDescription(),
                            "weight", product.getWeight()
                    )
            )).build();
        } else {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of(
                            "success", false,
                            "message", "Failed to insert product or producer is disabled"
                    ))
                    .build();
        }
    }

    /**
     * Simple health check endpoint.
     */
    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of("status", "UP")).build();
    }
}

