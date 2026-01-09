package com.redhat.demo.producer;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service responsible for inserting generated products into MySQL.
 */
@ApplicationScoped
public class DataProducerService {

    private static final Logger LOG = Logger.getLogger(DataProducerService.class);

    private static final String INSERT_SQL = 
            "INSERT INTO products (name, description, weight) VALUES (?, ?, ?)";
    
    private static final String COUNT_SQL = "SELECT COUNT(*) FROM products";

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ProductGenerator productGenerator;

    @ConfigProperty(name = "producer.enabled", defaultValue = "true")
    boolean producerEnabled;

    private final AtomicLong insertCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /**
     * Generates and inserts a new product into the database.
     * 
     * @return the generated product, or null if disabled or error occurred
     */
    public Product produceData() {
        if (!producerEnabled) {
            LOG.debug("Producer is disabled, skipping data generation");
            return null;
        }

        Product product = productGenerator.generateProduct();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, product.getName());
            stmt.setString(2, product.getDescription());
            stmt.setBigDecimal(3, product.getWeight());
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        product.setId(rs.getInt(1));
                    }
                }
                insertCount.incrementAndGet();
                LOG.infof("Inserted product: %s (Total inserts: %d)", product, insertCount.get());
                return product;
            }
        } catch (SQLException e) {
            errorCount.incrementAndGet();
            LOG.errorf(e, "Failed to insert product: %s", product.getName());
        }
        
        return null;
    }

    /**
     * Gets the total number of products in the database.
     */
    public long getProductCount() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(COUNT_SQL)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            LOG.error("Failed to count products", e);
        }
        return -1;
    }

    /**
     * Gets the number of successful inserts since startup.
     */
    public long getInsertCount() {
        return insertCount.get();
    }

    /**
     * Gets the number of failed inserts since startup.
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * Checks if the producer is enabled.
     */
    public boolean isEnabled() {
        return producerEnabled;
    }
}

