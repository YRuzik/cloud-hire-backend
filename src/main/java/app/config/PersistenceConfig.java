package app.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

public class PersistenceConfig {
    public static EntityManagerFactory getEntityManagerFactory() {
        Dotenv dotenv = Dotenv.configure().load();
        String dbUrl = dotenv.get("DATABASE_URL");
        String dbUsername = dotenv.get("DATABASE_USERNAME");
        String dbPassword = dotenv.get("DATABASE_PASSWORD");
        String dbDriver = dotenv.get("DATABASE_DRIVER");

        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", dbUrl);
        properties.put("jakarta.persistence.jdbc.user", dbUsername);
        properties.put("jakarta.persistence.jdbc.password", dbPassword);
        properties.put("jakarta.persistence.jdbc.driver", dbDriver);

        return Persistence.createEntityManagerFactory("CloudHire", properties);
    }
}
