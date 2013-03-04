package gov.nih.ncgc.bardplugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.catalina.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;


/**
 * This plugin is represents the plugin registry resource.
 * <p/>
 * Its basic task is to list the available plugins. This is an example of
 * a plugin that does not use Jersey at all, and instead is a basic servlet.
 * <p/>
 * Note that this plugin will not show itself in the list of plugins and will
 * not pass the BARD Plugin Validation tests.
 * <p/>
 * The plugin somewhat different from other plugins in that it implements
 * {@link ContainerServlet} which must run in a privileged context. As a result
 * do not deploy this plugin in the same context as other plugins.
 *
 * @author Rajarshi Guha
 */
public class Registry extends HttpServlet implements ContainerServlet {

    Wrapper wrapper;
    Host host;
    Context context;

    public Wrapper getWrapper() {
        return wrapper;
    }

    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
        if (wrapper == null) {
            context = null;
            host = null;
        } else {
            context = (org.apache.catalina.Context) wrapper.getParent();
            host = (Host) context.getParent();
        }
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws IOException, ServletException {
        String[] toks = request.getRequestURI().split("/");
        String command = toks[toks.length - 1];
        response.setContentType("text/plain; charset=UTF-8");
        PrintWriter writer = response.getWriter();
        if (command.equals("list")) {
            response.setContentType("application/json");
            listPlugins(writer);
        } else if (command.equals("") || command.equals("/")) {
            response.setContentType("text/plain");
            writer.println("BARD Plugin Registry");
        } else response.sendError(HttpServletResponse.SC_NOT_FOUND);
        writer.flush();
        writer.close();
    }

    public void listPlugins(PrintWriter writer) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode anode = mapper.createArrayNode();

        Container[] containers = host.findChildren();
        for (Container container : containers) {
            org.apache.catalina.Context context = (org.apache.catalina.Context) container;

            String path = context.getPath();
            String docBase = context.getDocBase();
            boolean isRunning = context != null && context.getAvailable();

            if (!path.startsWith("/bardplugin_")) continue;
            if (path.equals("/bardplugin_registry")) continue;

            String pluginRoot = path.split("_")[1];
            ObjectNode node = mapper.createObjectNode();
            node.put("path", "/plugins/" + pluginRoot);
            node.put("available", isRunning);
            anode.add(node);
        }
        String json = mapper.writeValueAsString(anode);
        writer.print(json);
    }

}