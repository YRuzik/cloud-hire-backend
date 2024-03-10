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

        em.getTransaction().begin();
        em.getTransaction().commit();

        em.close();
        emf.close();

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8000), 0);
        server.start();
        System.out.println("Server started in port 8000");
    }
}
