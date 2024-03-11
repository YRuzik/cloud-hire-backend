package app;

import app.config.PersistenceConfig;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) throws IOException {
        EntityManagerFactory emf = PersistenceConfig.getEntityManagerFactory();
        EntityManager em = emf.createEntityManager();

        initializeDatabase(em);
        startServer();

        System.out.println("Server started in port 8000");
    }

    private static void initializeDatabase(EntityManager em) {
        em.getTransaction().begin();
        em.getTransaction().commit();
        em.close();
    }

    private static void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8000), 0);
        server.start();
    }
}