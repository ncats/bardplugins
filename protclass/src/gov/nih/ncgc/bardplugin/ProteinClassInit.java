package gov.nih.ncgc.bardplugin;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * @author Rajarshi Guha
 */
public class ProteinClassInit implements ServletContextListener {

    Logger log;
    private Connection conn;

    public ProteinClassInit() {
        log = Logger.getLogger(this.getClass().getName());
    }

    public void contextInitialized(ServletContextEvent servletContextEvent) {
        try {
            Class.forName("org.h2.Driver");
            ServletContext servletContext = servletContextEvent.getServletContext();
            String url = getParameter(servletContext, "db.url", "jdbc:h2:");
//            String url = "jdbc:h2:/Users/guhar/src/bard.plugins/protclass/web/WEB-INF/pantherdb";
            conn = DriverManager.getConnection(url, "sa", "");
            servletContext.setAttribute("connection", conn);
            log.info("Got connection to " + url);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getParameter(ServletContext servletContext, String key, String defaultValue) {
        String value = servletContext.getInitParameter(key);
        return value == null ? defaultValue : value;
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        try {
            Statement stat = conn.createStatement();
            stat.execute("SHUTDOWN");
            stat.close();
            log.info("Shutdown H2 connection");
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
